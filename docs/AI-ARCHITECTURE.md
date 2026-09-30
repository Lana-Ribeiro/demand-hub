# Arquitetura de IA

## 1. Separação de responsabilidades

| Sistema (determinístico) | IA (assistiva) |
|---|---|
| Permissões, workflow, gates, aprovações, auditoria, integrações, persistência | Interpretar, extrair, resumir, sugerir, detectar lacunas, redigir especificações/prompts/documentação |

Agentes **retornam dados estruturados**; somente serviços da aplicação persistem ou
alteram estado. Nenhum agente tem acesso a repositórios, APIs de escrita ou credenciais.

## 2. Camadas

```text
Controller/Service de domínio
        │ pede análise
        ▼
AgentOrchestrator ── seleciona agente, monta contexto mínimo, executa,
        │            valida saída (schema), registra AiAgentRun, devolve resultado
        ▼
Agent<I,O> (IntakeAssistantAgent, DocumentExtractionAgent, ...)
        │ PromptTemplate + contexto mínimo
        ▼
LlmClient (porta) ── AnthropicLlmClient | OpenAiCompatibleLlmClient (OpenAI/Azure) | MockLlmClient
```

- `LlmClient.complete(LlmRequest) → LlmResponse` (system, messages, maxTokens,
  temperature, `jsonMode`). Provedor e modelo por env: `AI_PROVIDER`, `AI_MODEL`,
  `AI_API_KEY`, `AI_BASE_URL`, `AI_AZURE_DEPLOYMENT`, `AI_AZURE_API_VERSION`.
- **MockLlmClient**: não chama rede; retorna `mode=MOCK`. Os agentes, quando em modo
  mock, usam uma heurística local explícita (`mockFallback`) para que o fluxo seja
  exercitável; toda saída mock é marcada na UI como "Sugestão gerada em modo MOCK".

## 3. Agentes implementados

| Agente | Entrada mínima | Saída | Persistência pela aplicação |
|---|---|---|---|
| Intake Assistant | campos preenchidos, catálogo de campos, últimas 12 mensagens | resposta + sugestões de campo | `ChatMessage`, `AiSuggestion(FIELD_VALUE, source=CHAT)` |
| Document Extraction | texto do documento (≤ 30k chars, delimitado) + catálogo | campo, valor, confiança, trecho | `AiSuggestion(FIELD_VALUE, source=DOCUMENT)` |
| Presentation Extraction | texto por slide | idem | `AiSuggestion(source=PRESENTATION)` |
| Completeness | regras obrigatórias (determinístico) + qualidade textual (LLM) | lacunas | `AiSuggestion(MISSING_INFO)` |
| Consistency | valores do formulário × valores extraídos (determinístico) | divergências | `AiSuggestion(INCONSISTENCY)` |
| Duplicate Detection | candidatos por similaridade lexical (determinístico, inclui legado) + juízo LLM | possíveis duplicidades | `AiSuggestion(DUPLICATE)` |
| Classification | título, objetivo, problema, tipos/projetos ativos | tipo e projeto sugeridos | `AiSuggestion(FIELD_VALUE)` |
| Priority | resumo, impacto, urgência, prazo, dependências, **política de prioridade configurada** | prioridade + justificativa | `AiSuggestion(FIELD_VALUE)` |
| Impact | impacto, áreas, usuários, regulatório | nível e análise | seção da análise |
| Feasibility | escopo, sistemas, dependências, riscos | viabilidade/complexidade | seção da análise |
| Summary | campos do formulário + trechos extraídos aceitos | resumo executivo estruturado | `AiAnalysis(TRIAGE)` |
| Refinement | notas de reunião | requisitos, critérios, decisões, riscos, dúvidas | `AiSuggestion(REFINEMENT_ITEM)` → aceitos viram `RefinementItem` |
| Architecture | requisitos, decisões, sistemas | componentes, integrações, riscos, proposta | `AiAnalysis(ARCHITECTURE)` |
| Technical Specification | refinamento + arquitetura | especificação técnica | `AiAnalysis(TECH_SPEC)` |
| Prompt Engineer | dados estruturados da demanda | prompt técnico (template determinístico + orientações do LLM) | `AiAnalysis(TECH_PROMPT)` |
| Development Orchestrator | especificação | plano do squad (papéis, entradas/saídas, critérios) | `AiAnalysis(SQUAD_PLAN)` |
| Progress Summary | evento de execução | texto do comentário Jira (template configurado) | comentário Jira |
| Technical / Client Documentation | demanda, refinamento, arquitetura, execução | Markdown | `Document(TECHNICAL_DOC / CLIENT_DOC)` |

A política de prioridade é **dado** (tabela `priorities` + descrição), passada ao
agente como contexto — a decisão final é humana.

## 4. Squad de desenvolvimento (Estratégia B)

Registro `DevelopmentSquadRegistry` define para cada agente: responsabilidade,
contexto, ferramentas permitidas, entradas, saídas, critérios de conclusão e regras de
segurança — Orchestrator, Analyst, Architect, Tech Lead, Developer, Code Review, QA,
Documentation. A plataforma gera o **plano/manifesto** e o anexa ao item GitLab. A
execução autônoma (ex.: Claude Code headless em pipeline GitLab) é um runner externo
**não habilitado** no MVP — não existe botão que finja executar.

## 5. Rastreabilidade (IA + auditoria)

Cada execução gera `AiAgentRun` (agente, provedor, modelo, modo, entrada, saída,
duração, status). Cada sugestão referencia o run. A decisão humana (`ACCEPTED`,
`EDITED`, `REJECTED`) grava valor final, autor, data, motivo e um `AuditLog`
(`AI_SUGGESTION_DECIDED`) com `aiSuggestionId`. Alterações de campo originadas de
sugestão gravam `AuditLog` do campo com referência à sugestão.

## 6. Proteção contra prompt injection

1. Conteúdo de documentos/chat é sempre **dado**: inserido entre delimitadores
   `<documento_nao_confiavel>…</documento_nao_confiavel>`, e o system prompt instrui a
   ignorar instruções contidas nele.
2. Saída exigida em JSON com schema; saída inválida → `INVALID_OUTPUT`, nada persiste.
3. Campos sugeridos são validados contra o catálogo (whitelist); valores de enum
   validados; tamanhos limitados.
4. Agentes não possuem ferramentas de escrita; sugestões sempre passam por decisão humana.
5. Texto extraído truncado (limite configurável) e sem metadados/segredos.

## 7. RAG (preparado)

Porta `KnowledgeRetriever.retrieve(query, scope)`; implementação atual
`NoopKnowledgeRetriever` retorna vazio. Quando o contexto for insuficiente, agentes
devem responder "Informação insuficiente para concluir." (instrução no system prompt).
Futuro: pgvector + ingestão de políticas, padrões arquiteturais e histórico autorizado.
