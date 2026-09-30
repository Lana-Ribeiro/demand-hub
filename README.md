# Demand Hub — Diretoria Transformação de Redes

Plataforma corporativa de gestão do ciclo de vida de demandas: abertura guiada (formulário + documentos + apresentação +
assistente de IA), triagem assistida, approval gates configuráveis, Jira PMO (lifecycle da demanda), refinamento,
arquitetura, execução técnica no GitLab ou GitHub (lifecycle técnico), documentação e dashboard gerencial.

> **O processo é determinístico, a IA é assistiva, as decisões são rastreáveis e as ferramentas corporativas são integradas.**

## Estrutura

| Pasta | Conteúdo |
|---|---|
| `docs/` | Blueprint, arquitetura, domínio, workflow, integrações, IA, segurança e backlog |
| `backend/` | Java 21 · Spring Boot 3.3 · JPA · Flyway · Spring Security (JWT) · Apache Tika |
| `frontend/` | Angular 17 (standalone) · Angular Material |
| `docker-compose.yml` | PostgreSQL + backend + frontend |
| `.env.example` | Todas as variáveis (copie para `.env`; nunca versione o `.env`) |

## Executar localmente (sem Docker)

Pré-requisitos: JDK 21, Maven 3.9, Node 20.

```bash
cp .env.example .env
```

Preencha no `.env` ao menos `JWT_SECRET` (≥ 32 caracteres) e `DEV_SEED_PASSWORD` (senha dos usuários de desenvolvimento).

Backend com H2 em arquivo e dados de exemplo (perfis `dev,local`):

```bash
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev,local
```

Frontend (proxy `/api` → `localhost:8080`):

```bash
npm --prefix frontend install
```

```bash
npm --prefix frontend start
```

Acesse http://localhost:4200. Usuários de desenvolvimento (senha = `DEV_SEED_PASSWORD`): `cliente@`, `pmo@`, `gestor@`,
`diretor@`, `arquiteto@`, `dev@`, `qa@`, `admin@` — todos `@demandhub.local`.

## Executar com Docker (PostgreSQL)

Defina também `DATABASE_PASSWORD` no `.env` e execute:

```bash
docker compose up --build
```

## Testes

```bash
mvn -f backend/pom.xml test
```

Inclui os E2E de API exigidos: demanda técnica (cliente → IA → PMO → Jira → diretor → refinamento → arquitetura →
GitLab → QA via webhook → documentação → conclusão) e demanda não técnica (PMO → capacidade → gestor → execução
operacional → conclusão, sem GitLab), execução técnica no GitHub com webhook assinado (HMAC), além de segurança/RBAC, upload, legado, duplicidade e dashboard.

```bash
npm --prefix frontend test -- --watch=false --browsers=ChromeHeadless
```

## IA e integrações

| Variável | Valores | Padrão (dev) |
|---|---|---|
| `AI_PROVIDER` | `anthropic` (modelo padrão `claude-opus-5-5`), `openai`, `azure-openai`, `mock` | `mock` |
| `JIRA_MODE` | `real`, `mock`, `disabled` | `mock` |
| `SCM_PROVIDER` | `gitlab`, `github` (repositório da execução técnica) | `gitlab` |
| `GITLAB_MODE` / `GITHUB_MODE` | `real`, `mock`, `disabled` | `mock` |

Tudo que é simulado aparece com o selo **MOCK** na interface e é gravado com `mode=MOCK`. Em modo MOCK a IA usa
heurísticas locais explícitas, e mudanças de status do GitLab podem ser simuladas na aba *Execução* (pelo mesmo
processador dos webhooks reais).

Webhooks: `POST /api/integrations/gitlab/webhook` (header `X-Gitlab-Token` = `GITLAB_WEBHOOK_SECRET`),
`POST /api/integrations/github/webhook` (secret `GITHUB_WEBHOOK_SECRET`, evento *Issues*, JSON) e
`POST /api/integrations/jira/webhook?token=<JIRA_WEBHOOK_SECRET>`.

## Pendências conhecidas

Ver `docs/MVP-BACKLOG.md` → “Próximos itens”: SSO Entra ID, campos reais do Microsoft Forms, Azure Blob, RAG com
pgvector, runner do squad de agentes, E2E de interface (Playwright), anexos no Jira.
