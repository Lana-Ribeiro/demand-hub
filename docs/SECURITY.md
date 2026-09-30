# Segurança

## 1. Autenticação

- Login `POST /api/auth/login` (e-mail + senha, BCrypt) → JWT HS256 (8h) assinado com
  `JWT_SECRET` (≥ 32 bytes, obrigatório em `prod`; em `dev`, se ausente, é gerado
  aleatoriamente a cada inicialização).
- Backend é um OAuth2 Resource Server: substituível por Entra ID (Azure AD) trocando o
  `JwtDecoder` e o mapeamento de grupos → papéis.
- Front guarda o token em `sessionStorage` (escopo da aba) e envia `Authorization: Bearer`.

## 2. Autorização (RBAC)

Autorização em duas camadas: `@PreAuthorize` por permissão nos endpoints + regras de
propriedade nos serviços (cliente só acessa as próprias demandas).

| Permissão | CLIENT | PMO | MANAGER | DIRECTOR | ARCHITECT | DEVELOPER | QA | ADMIN |
|---|---|---|---|---|---|---|---|---|
| DEMAND_CREATE | ✔ | ✔ | ✔ | | | | | ✔ |
| DEMAND_VIEW_OWN | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| DEMAND_VIEW_ALL | | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| DEMAND_EDIT_ALL | | ✔ | | | | | | |
| DEMAND_TRIAGE | | ✔ | | | | | | |
| DEMAND_TRANSITION | | ✔ | | | | | | |
| DEMAND_RESPOND | ✔ | ✔ | ✔ | | | | | ✔ |
| APPROVAL_DECIDE | | ✔ | ✔ | ✔ | ✔ | | | |
| REFINEMENT_MANAGE | | ✔ | | | ✔ | | | |
| ARCHITECTURE_MANAGE | | | | | ✔ | | | |
| EXECUTION_VIEW | | ✔ | ✔ | | ✔ | ✔ | ✔ | ✔ |
| EXECUTION_MANAGE | | ✔ | | | | ✔ | | |
| QA_RECORD | | | | | | | ✔ | |
| DASHBOARD_VIEW | | ✔ | ✔ | ✔ | | | | ✔ |
| AUDIT_VIEW | | ✔ | | | | | | ✔ |
| AI_USE | ✔ | ✔ | | | ✔ | ✔ | | |
| ADMIN_USERS / ADMIN_CONFIG / INTEGRATION_MANAGE / LEGACY_IMPORT | | | | | | | | ✔ |

`APPROVAL_DECIDE` permite decidir apenas aprovações cujo `approverRole` o usuário possui.
Transições `systemOnly` não podem ser disparadas por usuários.

## 3. Proteção de APIs e entrada

- Bean Validation em todos os DTOs; tamanhos máximos em textos.
- Erros padronizados RFC 7807 sem stack trace.
- CORS restrito a `APP_CORS_ORIGINS`.
- CSRF desabilitado (API stateless com bearer token; nenhum cookie de sessão).
- Lock otimista em `Demand` (`version`).

## 4. Upload de arquivos

- Limite `UPLOAD_MAX_SIZE_MB` (padrão 25 MB).
- Whitelist de extensões e de MIME **detectado pelo conteúdo** (Tika):
  pdf, docx, doc, pptx, ppt, xlsx, xls, txt, md, csv, png, jpg.
- Nome original sanitizado; arquivo salvo com chave UUID fora do webroot; SHA-256 gravado.
- Download somente por quem pode ver a demanda; `Content-Disposition: attachment`.

## 5. Secrets

- Nenhuma credencial no código ou no repositório. `.env` está no `.gitignore`;
  `.env.example` só contém placeholders.
- Senhas do seed de desenvolvimento vêm de `DEV_SEED_PASSWORD` (perfil `dev` apenas).

## 6. Webhooks

- Jira: token compartilhado (`JIRA_WEBHOOK_SECRET`) comparado em tempo constante.
- GitLab: header `X-Gitlab-Token` (`GITLAB_WEBHOOK_SECRET`) comparado em tempo constante.
- Segredo não configurado → webhook recusado (401).

## 7. Logs

Sem senhas, tokens, conteúdo integral de documentos ou prompts nos logs de aplicação.
Prompts/respostas ficam em `ai_agent_runs`, acessíveis apenas com `AUDIT_VIEW`.

## 8. IA

Ver `AI-ARCHITECTURE.md §6` (prompt injection, whitelist de campos, sem escrita direta).
