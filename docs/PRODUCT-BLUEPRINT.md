# Product Blueprint — Demand Hub

> Plataforma corporativa de gestão do ciclo de vida de demandas/iniciativas da
> **Diretoria Transformação de Redes**, com IA assistiva.
>
> *O processo é determinístico, a IA é assistiva, as decisões são rastreáveis e as
> ferramentas corporativas são integradas.*

## 1. Fontes analisadas (Fase 0)

O repositório inicial continha apenas cinco fotografias de anotações manuscritas:

| Arquivo | Conteúdo relevante |
|---|---|
| `Desenho escopo arquitetura.jpeg` | Fluxo macro: cliente abre demanda na ferramenta PMO → OK do PMO cria card no Jira PMO → ao seguir para o time de desenvolvimento, Jira (fila dev) ou GitLab com agentes → "parte dev" alimenta de volta o Jira PMO com atualizações de status. Um agente dá sugestões de priorização e triagem ao PMO; após aprovação, entra automaticamente no Jira como novo card. |
| `Desenho escopo ponto 1 e 2.jpeg` | (1) Cliente abre a demanda, preenche campos e anexa a documentação (template) e o Canva/PPT padrão; preenchimento manual e/ou automático a partir dos documentos; chatbot com IA para auxiliar. (2) PMO é avisado por e-mail/Teams; permissão específica para PMO; Kanban ou outra visão; **duas visões**: demanda resumida (feita por agente — P1, orçamento, OK diretor, resumo) e demanda completa; ambas precisam do acordo do PMO. |
| `Desenho escopo ponto 3 e 4.jpeg` | (3) Após OK do PMO a demanda vira card no Jira (Kanban), identificada por cor do projeto, com resumo e documentação. O agente gera resumo de triagem, qualificação e análise de viabilidade; o PMO pode modificar, excluir e inserir itens, inclusive priorização — sempre com aprovação do PMO. Dashboard para gerente (demandas em aberto, projeto com mais aberturas). (4) Time de desenvolvimento recebe via Jira e e-mail. |
| `Desenho escopo ponto 5.jpeg` | Card detalhado com o que foi definido pelo PMO e arquitetura; etapas de refinamento no Jira PMO; em desenvolvimento, a cada progresso na fila de desenvolvimento, um resumo é inserido nos comentários do Jira. Duas possibilidades de desenvolvimento: 1) prompt estruturado; 2) squad de agentes. Etapa final: deploy e duas documentações (técnica e cliente). A cada repasse, documentação e e-mail. |
| `Dicas ponto de atenção.jpeg` | Pontos de atenção (Fernanda): solicitações que não são desenvolvimento; como saber o status para atualização; alocação de pessoas; gestão de riscos; mapeamento de processos; chatbot para abrir demanda; de acordo com diretor; direcionar para o time correto; legados (o que fazer com o histórico); dashboard. |

**Suposição documentada:** as imagens do Microsoft Forms "Abertura de Iniciativas para a
Diretoria Transformação de Redes" não foram disponibilizadas. Os campos do formulário
(ver `DOMAIN-MODEL.md §Demand`) foram inferidos do processo descrito e das práticas
usuais de abertura de iniciativas. Ao receber o formulário real, ajustar
`DemandFormFields` (backend) e `demand-form` (frontend) — a arquitetura suporta
campos adicionais via `additionalData` sem migração.

## 2. Problema

Informações de uma demanda ficam espalhadas entre Forms, documentos, PPT/Canva,
e-mail, Teams, Jira, GitLab e reuniões. O PMO consolida manualmente, a triagem é
lenta, não há rastreabilidade de decisões e o status técnico não chega de forma
confiável ao acompanhamento de negócio.

## 3. Visão do produto

Um portal único onde:

1. O **cliente** abre a demanda (formulário guiado + documentos + PPT + chatbot).
2. A **IA** extrai, resume, aponta lacunas, divergências e duplicidades e **sugere**
   classificação/prioridade — nunca decide.
3. O **PMO** revisa, edita, aprova, rejeita ou solicita informações.
4. **Approval gates configuráveis** (diretor, gestor, arquitetura) bloqueiam o avanço.
5. A demanda aprovada vira card no **Jira PMO** (lifecycle da demanda).
6. **Refinamento** e **arquitetura** são registrados com apoio de agentes.
7. Demandas técnicas geram item no **GitLab** (lifecycle da execução), com prompt
   técnico ou plano para squad de agentes.
8. Eventos do GitLab atualizam a **Execução Técnica** e geram comentários no Jira sem
   alterar o status mestre da demanda.
9. Documentação técnica e para o cliente são geradas e revisadas.
10. Tudo é auditado; gestores acompanham por dashboard com dados reais.

## 4. Personas e papéis

| Papel | Objetivo principal |
|---|---|
| CLIENT | Abrir e acompanhar suas demandas, responder pendências |
| PMO | Triar, classificar, priorizar, aprovar/rejeitar, encaminhar |
| MANAGER | Acompanhar portfólio, aprovar alocação de recursos |
| DIRECTOR | Aprovar demandas que exigem aprovação executiva |
| ARCHITECT | Analisar e aprovar arquitetura |
| DEVELOPER | Consultar execução técnica e prompt técnico |
| QA | Registrar resultados de validação |
| ADMIN | Configurar usuários, projetos, workflows, regras e integrações |

## 5. Princípios não negociáveis

1. **Sistema controla** workflow, permissões, aprovações, estados, auditoria, integrações.
2. **IA sugere**; humano aceita/edita/rejeita; toda decisão sobre sugestão é auditada.
3. **Jira = Demand Lifecycle; GitLab = Technical Execution Lifecycle.** Nunca misturar.
4. Nenhuma regra de negócio dentro de prompt; nenhum LLM escreve no banco.
5. Nada hardcoded: projetos, tipos, workflows, regras de aprovação e status técnicos
   são dados configuráveis.
6. Mocks são explícitos (`mode = MOCK` visível na UI e na API).
7. Sem métricas fictícias: sem dados → "Dados insuficientes".
8. Legado importado não recebe histórico inventado.

## 6. Escopo por fase

| Fase | Entrega | Status |
|---|---|---|
| 0 Discovery | Blueprint, arquitetura, domínio, workflow, integrações, IA, segurança, backlog | ✅ |
| 1 Foundation | Backend/Frontend base, DB+migrations, JWT, RBAC, projetos, config, auditoria | ✅ |
| 2 Intake | Formulário por seções, rascunho, anexos, envio, protocolo, chatbot | ✅ |
| 3 AI Intake | Extração doc/PPT, completude, consistência, resumo, classificação, duplicidade | ✅ |
| 4 PMO | Kanban, tabela, filtros, detalhe resumido/completo, aprovar/rejeitar/pendência | ✅ |
| 5 Workflow | Tipos → workflows, approval gates configuráveis, diretor, arquitetura | ✅ |
| 6 Jira | Gateway REST real (Cloud v3) + mock explícito; criação, comentários, transições, webhook | ✅ (real requer credenciais) |
| 7 Refinement | Reuniões, decisões, requisitos, critérios, Refinement Agent | ✅ |
| 8 GitLab | Gateway REST real + mock; webhooks; execução técnica; sync Jira | ✅ (real requer credenciais) |
| 9 Dev Agents | Registro do squad, prompt técnico, plano de execução por agentes | ✅ arquitetura; runner externo não habilitado |
| 10 Documentation | Documentação técnica e cliente geradas e revisáveis | ✅ |
| 11 Management | Dashboard gerencial com indicadores reais e gargalos | ✅ |

O detalhamento está em `MVP-BACKLOG.md`.

## 7. Métricas de sucesso do produto

- Tempo médio de triagem (entrada em `TRIAGE` → saída) — medido em `demand_stage_history`.
- % de sugestões da IA aceitas sem edição (qualidade dos agentes) — `ai_suggestions`.
- % de demandas devolvidas por falta de informação — transições `REQUEST_INFO`.
- Tempo médio de refinamento e de execução.

## 8. Fora de escopo (MVP)

- SSO corporativo (Azure AD/Entra ID) — arquitetura preparada (resource server JWT),
  autenticação local no MVP.
- Execução autônoma de código por squad de agentes dentro da plataforma — a plataforma
  gera o plano/manifesto; a execução roda em runner externo (CI GitLab) a ser habilitado.
- Busca vetorial (RAG) — porta `KnowledgeRetriever` criada com implementação vazia
  que responde "Informação insuficiente para concluir".
