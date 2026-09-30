# MVP Backlog

Legenda — Prioridade: **Must / Should / Could**. Complexidade: P / M / G.
Impacto técnico: Baixo / Médio / Alto. Status: ✅ entregue · 🟡 parcial · ⬜ pendente.

---

## EPIC 1 — Foundation (Fase 1)
**Objetivo:** base segura, configurável e auditável.

### F1.1 Autenticação e RBAC — Must · M · Alto — ✅
- **US1.1.1** Como usuário, quero fazer login para acessar funcionalidades do meu papel.
  - AC: credenciais inválidas → 401 genérico; token expira em 8h; endpoints exigem token.
  - Tasks: entidades User/Role/Permission; migration; `AuthController`; `JwtService`; `SecurityConfig`; testes.
- **US1.1.2** Como ADMIN, quero gerenciar usuários e papéis.
  - AC: criar/desativar usuário; atribuir papéis; ação auditada.
  - Dependências: US1.1.1.

### F1.2 Projetos — Must · P · Médio — ✅
- **US1.2.1** Como ADMIN, quero cadastrar projetos com código, cor, ícone, responsáveis e chaves Jira/GitLab.
  - AC: código único; cor validada `#RRGGBB`; UI mostra código+nome (não só cor).

### F1.3 Catálogo e configuração — Must · M · Alto — ✅
- **US1.3.1** Tipos de demanda configuráveis apontando para workflows.
- **US1.3.2** Prioridades configuráveis com descrição da política.

### F1.4 Auditoria base — Must · M · Alto — ✅
- **US1.4.1** Toda ação relevante gera AuditLog (quem, quando, o quê, antes, depois, motivo).
  - AC: consulta filtrável por demanda/ator/ação; acesso restrito a AUDIT_VIEW.

## EPIC 2 — Demand Intake (Fase 2)
**Objetivo:** cliente abre demanda completa na plataforma.

### F2.1 Formulário e rascunho — Must · G · Alto — ✅
- **US2.1.1** Como cliente, quero salvar rascunho e continuar depois.
  - AC: rascunho aceita dados parciais; aparece em "Minhas demandas"; só o autor edita.
- **US2.1.2** Como cliente, quero um formulário por seções com campos condicionais.
  - AC: seção financeira só quando "impacto orçamentário"; descrição regulatória obrigatória quando marcado; justificativa de prazo obrigatória quando há data.
- **US2.1.3** Como cliente, quero enviar a demanda e receber protocolo.
  - AC: validação determinística de obrigatórios (lista de erros por campo); protocolo `DEM-AAAA-NNNNN`; notificação ao PMO.

### F2.2 Anexos — Must · M · Alto — ✅
- **US2.2.1** Anexar documentação e apresentação com validação de tipo/tamanho.

### F2.3 Chatbot de abertura — Must · G · Médio — ✅
- **US2.3.1** Conversar com o assistente que pergunta, explica campos e sugere preenchimentos.
  - AC: sugestões nunca aplicadas sem aceite; marcadas como sugestão; histórico persistido.

## EPIC 3 — AI Intake (Fase 3)
### F3.1 Extração de documentos/PPT — Must · G · Alto — ✅
- AC: texto extraído (Tika); sugestões com campo, valor, origem e confiança; revisão humana.
### F3.2 Completude e consistência — Must · M · Médio — ✅
- AC: lacunas listadas; divergência formulário × PPT exibida com ambas as fontes e decisão.
### F3.3 Duplicidade — Should · M · Médio — ✅
- AC: candidatos por similaridade (inclui legado) com score e justificativa.
### F3.4 Triagem IA — Must · G · Alto — ✅
- AC: resumo executivo, tipo, projeto, prioridade, impacto, urgência, complexidade, dependências, riscos, lacunas, duplicidades, recomendação; tudo editável pelo PMO; falha da IA não bloqueia a triagem.

## EPIC 4 — PMO (Fase 4)
### F4.1 Visões — Must · G · Médio — ✅
- Kanban por categoria de estágio; tabela com filtros (projeto, prioridade, tipo, solicitante, responsável, estágio, período, impacto); detalhe resumido e completo.
### F4.2 Decisões do PMO — Must · M · Alto — ✅
- Aprovar, rejeitar (motivo), solicitar informação, alterar classificação/prioridade/projeto/responsável; auditoria antes/depois/motivo.

## EPIC 5 — Workflow (Fase 5)
### F5.1 Engine configurável — Must · G · Alto — ✅
- AC: transições por dados; gates bloqueiam avanço com mensagem clara; histórico de estágios.
### F5.2 Approval gates — Must · M · Alto — ✅
- AC: regras com condição (campo/operador/valor); diretor, gestor, arquiteto; admin edita regras sem deploy.

## EPIC 6 — Jira (Fase 6) — Must · G · Alto — ✅ (REAL requer credenciais)
- Criação no aprovar; transições por `jiraStatus`; comentários; webhook de comentários; retry; selo MOCK.

## EPIC 7 — Refinement (Fase 7) — Must · G · Médio — ✅
- Reuniões, decisões, itens (requisitos, critérios, riscos, dúvidas, dependências); Refinement Agent a partir de notas; gate REFINEMENT_COMPLETE.

## EPIC 8 — GitLab e Execução (Fase 8) — Must · G · Alto — ✅ (REAL requer credenciais)
- Issue criada em READY_FOR_DEVELOPMENT com contexto completo; webhook de labels; status configuráveis; comentário no Jira sem alterar estágio; painel "Ciclo da Demanda × Execução Técnica".

## EPIC 9 — Development Agents (Fase 9) — Should · G · Médio — 🟡
- ✅ Architecture Agent, Technical Spec Agent, Prompt Engineer Agent, registro do squad e plano.
- ⬜ Runner externo de execução autônoma (GitLab CI + Claude Code headless) — requer decisão de segurança/infra.

## EPIC 10 — Documentation (Fase 10) — Must · M · Médio — ✅
- Documentação técnica e para cliente geradas, editáveis e anexadas; gate DOCUMENTATION_GENERATED.

## EPIC 11 — Management (Fase 11) — Should · M · Médio — ✅
- Dashboard com contagens por categoria/projeto/tipo/prioridade, aprovações pendentes, paradas (> N dias), tempos médios (triagem, refinamento, execução) com "Dados insuficientes", volume mensal.

## EPIC 12 — Legado — Should · P · Baixo — ✅
- Importação JSON; `source=LEGACY`, somente leitura, sem histórico inventado.

## EPIC 13 — Notificações — Must · M · Médio — ✅
- `NotificationService` central; IN_APP; EMAIL (SMTP) e TEAMS (webhook) por configuração; SKIPPED quando desabilitado.

---

## EPIC 14 — Interface (Angular) — Must · G · Alto — ✅
- 19 telas: login, início, minhas demandas, nova demanda (seções, condicionais, autosave, chatbot, documentos, sugestões),
  detalhe (resumo/completa/análise IA/pendências/aprovações/refinamento/arquitetura e prompt/execução/documentação/histórico),
  Kanban, tabela com filtros, aprovações, execução técnica, dashboard, notificações, auditoria, usuários, projetos,
  workflows e regras, catálogos, legado, agentes de IA.
- Testes unitários (Karma) do renderizador Markdown seguro, guard de rotas e tratamento de erros.

## Próximos itens (pós-MVP)
| Item | Prioridade | Complexidade |
|---|---|---|
| SSO Entra ID | Must | M |
| Formulário real do Microsoft Forms (ajuste de campos) | Must | P |
| Azure Blob Storage | Should | P |
| RAG com pgvector | Should | G |
| Runner de squad de agentes | Could | G |
| E2E de UI com Playwright | Should | M |
| Anexos para Jira | Could | P |
