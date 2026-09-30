# Arquitetura

## 1. Visão geral

```text
┌──────────────────────────┐        ┌───────────────────────────────────────────┐
│ Frontend Angular 17      │  REST  │ Backend Spring Boot 3.3 (Java 21)         │
│ standalone + Material    │ ─────► │ Monólito modular                          │
│ JWT em memória/session   │  JSON  │                                           │
└──────────────────────────┘        │  identity  project  catalog  demand       │
                                    │  document  ai  workflow  approval         │
                                    │  refinement  execution  integration       │
                                    │  notification  audit  dashboard  shared   │
                                    └──────┬──────────┬───────────┬─────────────┘
                                           │          │           │
                                   PostgreSQL   Storage (FS)   Gateways externos
                                   (Flyway)     porta p/ Blob  Jira · GitLab · LLM
                                                               SMTP · Teams
```

**Decisão:** monólito modular (um deployable, pacotes com fronteiras claras) em vez de
microserviços. Justificativa: time pequeno, domínio ainda em descoberta, transações
consistentes entre workflow/auditoria. As fronteiras por pacote e a comunicação por
eventos permitem extrair módulos (ex.: `ai`, `integration`) no futuro.

## 2. Estrutura do repositório

```text
backend/            Spring Boot (Maven)
  src/main/java/com/demandhub/platform/<módulo>
  src/main/resources/db/migration   Flyway (V1__, V2__ ...)
frontend/           Angular 17
docs/               Documentação de produto e arquitetura
docker-compose.yml  PostgreSQL + backend + frontend
.env.example        Variáveis de ambiente (sem secrets)
```

## 3. Módulos do backend

| Módulo | Responsabilidade | Não faz |
|---|---|---|
| `shared` | Erros padronizados (RFC 7807), eventos base, utilitários de segurança | Regra de negócio |
| `identity` | Usuários, papéis, permissões, login JWT, `CurrentUser` | — |
| `project` | Projetos (código, cor, ícone, responsáveis, chaves Jira/GitLab) | — |
| `catalog` | Tipos de demanda (→ workflow), prioridades | — |
| `demand` | Entidade principal, rascunho, formulário, envio, protocolo, pendências, comentários, legado | Transição de estado (delegada ao `workflow`) |
| `document` | Upload validado, storage (porta), extração de texto (Tika) | Interpretação (IA) |
| `ai` | Porta `LlmClient` (Anthropic/OpenAI/Azure/Mock), agentes, orquestrador, runs, sugestões, chat | Alterar estado/aprovações |
| `workflow` | Definições, estágios, transições, `WorkflowEngine`, requisitos de saída, ações de entrada | Chamar APIs externas diretamente |
| `approval` | Regras configuráveis, avaliação determinística, aprovações pendentes | — |
| `refinement` | Reuniões, decisões, itens de refinamento (requisitos, critérios, riscos…) | — |
| `execution` | Execução técnica (lifecycle GitLab), status configuráveis, eventos | Alterar stage da demanda |
| `integration` | `JiraGateway`, `GitLabGateway` (REAL/MOCK), links externos, webhooks | — |
| `notification` | `NotificationService` central; canais IN_APP, EMAIL, TEAMS | — |
| `audit` | `AuditService`, consulta de trilha | — |
| `dashboard` | Indicadores reais agregados | Inventar métricas |

Cada módulo segue: `domain` (entidades/enums) → `repository` → `service` (regras) →
`web` (controllers + DTOs). Controllers não contêm regra de negócio.

## 4. Comunicação entre módulos

- **Chamadas diretas** a serviços públicos de outro módulo quando a operação é síncrona
  e transacional (ex.: `WorkflowEngine` chama `ApprovalService.evaluateOnEnter`).
- **Eventos de domínio** (Spring `ApplicationEventPublisher`) para efeitos colaterais:
  notificações, integrações, análise de IA. Listeners críticos usam
  `@TransactionalEventListener(AFTER_COMMIT)` para não publicar efeitos de transações
  revertidas. Tarefas longas (IA, APIs externas) rodam em executor assíncrono
  configurável (`app.async.enabled`).

Eventos: `DemandSubmitted`, `DemandStageChanged`, `DemandCompleted`, `DemandRejected`,
`InformationRequested`, `ApprovalRequested`, `ApprovalDecided`,
`ExecutionStatusChanged`, `AnalysisCompleted`.

## 5. Stack e decisões técnicas

| Tema | Decisão | Motivo |
|---|---|---|
| Backend | Java 21, Spring Boot 3.3, Spring Data JPA, Validation, Security | Requisito; maturidade |
| Autenticação | JWT HS256 emitido pelo backend (`spring-security-oauth2-resource-server`) | Stateless; trocável por Entra ID (mesmo resource server) |
| Banco | PostgreSQL 16; Flyway | Requisito |
| Testes | JUnit 5, Spring Boot Test, MockMvc, H2 em modo PostgreSQL | Docker indisponível no ambiente de dev; SQL das migrations é portável (sem `jsonb`, JSON armazenado em `TEXT`) |
| Extração de texto | Apache Tika (PDF, DOCX, PPTX, XLSX, TXT) | Um único parser para todos os formatos |
| HTTP externo | `RestClient` (Spring 6.1) | Nativo, testável |
| Frontend | Angular 17 standalone, Angular Material, Signals, Reactive Forms | Requisito; UI corporativa acessível |
| Estado no front | Serviços com Signals por feature; sem NgRx (complexidade desnecessária) | Simplicidade |
| Configuração | Variáveis de ambiente; `.env` importado via `spring.config.import` | Sem secrets no código |

## 6. Configuração por ambiente

Perfis: `dev` (seed de dados de desenvolvimento, integrações MOCK por padrão),
`test` (H2, execução síncrona), `prod` (PostgreSQL, integrações conforme env).
Ver `.env.example`.

## 7. Observabilidade

- Logs estruturados com `demandId`/`protocol` em MDC nas operações de workflow.
- Nenhum log com tokens, senhas, conteúdo integral de documentos ou prompts completos
  (prompts ficam na tabela `ai_agent_runs`, com acesso restrito a `AUDIT_VIEW`).
- Spring Boot Actuator (`/actuator/health`, `/actuator/info`).

## 8. Evolução prevista

- SSO Entra ID: trocar `JwtDecoder` para issuer do tenant, mapear grupos → papéis.
- Storage Azure Blob/S3: nova implementação de `DocumentStorage`.
- RAG: implementação de `KnowledgeRetriever` com pgvector.
- Mensageria (Service Bus/Kafka) substituindo eventos in-process, se necessário.
