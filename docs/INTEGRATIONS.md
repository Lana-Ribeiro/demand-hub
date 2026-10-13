# Integrações

## 1. Princípios

- Cada sistema externo tem uma **porta** (interface) e implementações `REAL` e `MOCK`
  selecionadas por variável de ambiente (`JIRA_MODE`, `GITLAB_MODE`: `real | mock | disabled`).
- Todo `ExternalLink` grava `mode`; a UI exibe o selo **MOCK** quando aplicável. Mock
  nunca é apresentado como integração real.
- Falhas de integração **não bloqueiam** o workflow: o link fica `syncStatus=FAILED`
  com `lastError`, é auditado e pode ser reprocessado (`POST /demands/{id}/jira/sync`).
- Integrações são acionadas por `onEnterActions` do workflow ou por eventos, nunca por
  código espalhado nos controllers.

## 2. Jira PMO (lifecycle da demanda)

Porta `JiraGateway`:

| Operação | Jira Cloud REST v3 |
|---|---|
| `createIssue` | `POST /rest/api/3/issue` — summary `[PROTOCOLO] título`, description ADF (resumo executivo + dados principais + link da plataforma), labels (`demand-hub`, código do projeto, tipo), prioridade mapeada |
| `addComment` | `POST /rest/api/3/issue/{key}/comment` |
| `transitionTo(statusName)` | `GET /rest/api/3/issue/{key}/transitions` → `POST` com o id cujo destino tem o nome configurado em `workflow_stages.jira_status` |
| `updateIssue` | `PUT /rest/api/3/issue/{key}` (prioridade/labels após edição do PMO) |

Autenticação: Basic (`JIRA_USER_EMAIL` + `JIRA_API_TOKEN`). Projeto: `project.jiraProjectKey`
ou `JIRA_PROJECT` padrão. Mapeamento de prioridade: `JIRA_PRIORITY_MAP`
(padrão `P1:Highest,P2:High,P3:Medium,P4:Low`).

Cada mudança de etapa transiciona o card (por nome de status) **e** publica um comentário com o nome exato da
etapa e o motivo — projetos com poucos status (ex.: team-managed com "Tarefas pendentes / Em análise /
Em andamento / Concluído") agrupam várias etapas no mesmo status. Se o projeto não tiver o campo Prioridade
na tela de criação, a issue é criada sem prioridade (a prioridade segue nas labels, ex.: `p3`). Ao trocar de
`JIRA_MODE=mock` para `real`, "Sincronizar/reprocessar Jira" recria no Jira real os cards criados em MOCK.

Anexos: não enviados automaticamente no MVP (limites/políticas de DLP); a descrição
contém link para a plataforma. Suporte previsto via `POST /issue/{key}/attachments`.

**Identidade do projeto:** label com o código do projeto + nome no summary; a cor é
aplicada na plataforma (Jira não suporta cor por card via API padrão).

Webhook `POST /api/integrations/jira/webhook?token=…` (`JIRA_WEBHOOK_SECRET`):
- `comment_created` → `Comment(source=JIRA)` na demanda (ignora comentários criados
  pela própria plataforma, marcados com o prefixo `[Demand Hub]`).
- `jira:issue_updated` com mudança de status → registrado em auditoria como evento
  externo. **Não** move o estágio (gates são da plataforma).

## 3. Repositório de código — GitLab ou GitHub (lifecycle da execução técnica)

Porta `ScmGateway`, provedor escolhido por `SCM_PROVIDER=gitlab | github`; cada um com
modo `real | mock | disabled` (`GITLAB_MODE` / `GITHUB_MODE`). A issue é criada quando a
demanda técnica entra em **Pronta para desenvolvimento** (ação de estágio `CREATE_SCM_ISSUE`),
com título `[PROTOCOLO] título`, descrição Markdown (contexto, requisitos, critérios, decisões,
arquitetura, link Jira, prompt técnico) e labels `demand-hub` + `status::todo`.

| Operação | GitLab REST v4 | GitHub REST |
|---|---|---|
| `createIssue` | `POST /api/v4/projects/{id}/issues` | `POST /repos/{owner}/{repo}/issues` |
| `addNote` | `POST /api/v4/projects/{id}/issues/{iid}/notes` | `POST /repos/{owner}/{repo}/issues/{number}/comments` |
| Autenticação | `PRIVATE-TOKEN` (`GITLAB_TOKEN`) | `Authorization: Bearer` (`GITHUB_TOKEN`: fine-grained com Issues read/write, ou clássico `repo`) |
| Repositório | `project.scmProjectRef` (id) ou `GITLAB_DEFAULT_PROJECT_ID` | `project.scmProjectRef` (`owner/repo`) ou `GITHUB_REPOSITORY` |

No GitHub, labels inexistentes (`status::qa` etc.) são criadas automaticamente ao serem aplicadas.

Webhooks (mesmo processador `ExecutionService.processScmIssueEvent`):
- GitLab: `POST /api/integrations/gitlab/webhook`, header `X-Gitlab-Token` = `GITLAB_WEBHOOK_SECRET`, evento *Issue Hook*.
- GitHub: `POST /api/integrations/github/webhook`, content type `application/json`, secret = `GITHUB_WEBHOOK_SECRET`
  (validado por HMAC `X-Hub-Signature-256`), evento *Issues*.
- Labels `status::*` → `ExecutionStatus` via `scm_label`; issue fechada sem label de status → status `done`.
- Fluxo: recebe evento → registra `WebhookEvent` → identifica `ExternalLink` → atualiza `TechnicalExecution` →
  `ExecutionEvent` → `ExecutionStatusChanged` → comentário no Jira com o template do status → notificação.
  O estágio da demanda **não** muda.

Modo MOCK: `POST /api/integrations/scm/mock/executions/{demandId}/status` (permissão `EXECUTION_MANAGE`)
simula a mudança de label e passa pelo **mesmo** processador dos webhooks.

## 4. LLM

Ver `AI-ARCHITECTURE.md`. `AI_PROVIDER = anthropic | openai | azure-openai | mock`.

## 5. Notificações

`NotificationService` é o único ponto de envio. Canais:

| Canal | Implementação | Config |
|---|---|---|
| IN_APP | tabela `notifications` | sempre ativo |
| EMAIL (Outlook/Exchange) | SMTP via Spring Mail | `MAIL_ENABLED, MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM` |
| TEAMS | Incoming Webhook / Workflows (POST JSON) | `TEAMS_ENABLED, TEAMS_WEBHOOK_URL` |

Canal desabilitado → registro `SKIPPED` (rastreável). Eventos: nova demanda,
pendência, aprovação necessária, rejeição, mudança de etapa, avanço técnico, conclusão.

## 6. Storage

Porta `DocumentStorage`; implementação `LocalFileSystemStorage` (`STORAGE_PATH`).
Chaves UUID, sem uso do nome original no caminho. Futuro: Azure Blob/S3.
