# Modelo de Domínio

## 1. Diagrama (simplificado)

```text
User ─┬─< UserRole >── Role ──< RolePermission >── Permission
      │
      ├─< Demand (requester) >── Project
      │        │ ├── DemandType ── WorkflowDefinition ──< WorkflowStage
      │        │ ├── Priority                          └─< WorkflowTransition
      │        │ ├── WorkflowStage (current)            └─< ApprovalRule
      │        │
      │        ├─< DemandStageHistory
      │        ├─< Document (upload + gerados)
      │        ├─< InformationRequest (pendências)
      │        ├─< Comment
      │        ├─< ChatMessage
      │        ├─< AiAgentRun ──< AiSuggestion
      │        ├─< AiAnalysis
      │        ├─< Approval ── ApprovalRule
      │        ├─< Meeting ──< Decision
      │        ├─< RefinementItem
      │        ├─< ExternalLink (JIRA | GITLAB)
      │        └─< TechnicalExecution ──< ExecutionEvent
      │
      ├─< Notification
      └─< AuditLog
ExecutionStatus (config: lifecycle técnico GitLab)
WebhookEvent (log de recebimento)
```

## 2. Entidades

### Identity
- **User** `id, email (único), fullName, passwordHash, area, active, createdAt`.
- **Role** `code (PK: CLIENT, PMO, MANAGER, DIRECTOR, ARCHITECT, DEVELOPER, QA, ADMIN), name, description`.
- **Permission** `code (PK), description`. Ligada a Role via `role_permissions`.

Permissões: `DEMAND_CREATE, DEMAND_VIEW_OWN, DEMAND_VIEW_ALL, DEMAND_EDIT_ALL,
DEMAND_TRIAGE, DEMAND_TRANSITION, DEMAND_RESPOND, APPROVAL_DECIDE, REFINEMENT_MANAGE,
ARCHITECTURE_MANAGE, EXECUTION_VIEW, EXECUTION_MANAGE, QA_RECORD, DASHBOARD_VIEW,
AUDIT_VIEW, AI_USE, ADMIN_USERS, ADMIN_CONFIG, INTEGRATION_MANAGE, LEGACY_IMPORT`.
Matriz completa em `SECURITY.md`.

### Project
`id, code (único, ex. SMARTDESK), name, description, color (#hex), icon, ownerId,
pmoId, jiraProjectKey, gitlabProjectId, active`. Identidade visual nunca depende só da
cor: UI sempre exibe código + nome (+ ícone).

### Catalog
- **DemandType** `id, code, name, description, technical (bool), workflowId, active`.
  Seed: DEVELOPMENT, IMPROVEMENT, AUTOMATION, INTEGRATION (workflow TECHNICAL);
  RESOURCE_ALLOCATION, OPERATIONAL_CHANGE (OPERATIONAL); CONSULTING, SUPPORT, OTHER (SERVICE).
- **Priority** `code (P1..P4), name, rank, color`.

### Demand (entidade principal)
| Grupo | Campos |
|---|---|
| Identificação | `id, protocol (DEM-AAAA-NNNNN, gerado no envio), title, source (PORTAL, CHATBOT, DOCUMENT, LEGACY)` |
| Relações | `requester, project?, demandType?, priority?, owner (responsável PMO)?, workflow?, currentStage?` |
| Estado | `lifecycleState (DRAFT, ACTIVE, ON_HOLD, REJECTED, COMPLETED, CANCELLED, LEGACY)` — derivado da categoria do estágio atual; `readOnly` |
| Solicitante | `requesterArea*, requesterManagement, requesterPhone, sponsorName*` |
| Iniciativa | `objective*, currentProblem*, justification*, expectedBenefits*, scopeDescription, outOfScope` |
| Impacto/prazo | `impactedAreas*, impactedUsersCount, systemsInvolved, impactLevel* (LOW..CRITICAL), urgency* (LOW..HIGH), desiredDate, deadlineJustification (obrigatório se desiredDate), regulatoryRequirement, regulatoryDescription (obrigatório se regulatório)` |
| Financeiro (condicional) | `hasBudgetImpact; se true: estimatedBudget*, costCenter*, budgetApproved, expectedReturn` |
| Envolvidos | `businessFocalPoint*, technicalFocalPoint, otherStakeholders` |
| Complementos | `dependencies, knownRisks, additionalNotes, additionalData (JSON livre para campos futuros)` |
| Legado | `originalId, originalCreatedAt, originalStatus, originalOwner` |
| Controle | `submittedAt, completedAt, createdAt, updatedAt, version (lock otimista)` |

`*` = obrigatório no envio (validação determinística em `DemandSubmissionValidator`);
rascunhos aceitam dados parciais.

### Workflow
- **WorkflowDefinition** `id, code, name, description, active`.
- **WorkflowStage** `id, workflowId, code, name, category, position, autoAdvance,
  jiraStatus, onEnterActions (CSV), exitRequirements (CSV)`.
  - `category`: `DRAFT, INTAKE, TRIAGE, ON_HOLD, ANALYSIS, APPROVAL, REFINEMENT,
    ARCHITECTURE, READY, EXECUTION, DOCUMENTATION, DONE, REJECTED, CANCELLED`.
  - `onEnterActions`: `RUN_TRIAGE_ANALYSIS, CREATE_JIRA_ISSUE, CREATE_SCM_ISSUE`.
  - `exitRequirements`: `APPROVALS_GRANTED, REFINEMENT_COMPLETE, EXECUTION_DONE,
    DOCUMENTATION_GENERATED, TRIAGE_FIELDS_SET, INFO_REQUESTS_ANSWERED`.
- **WorkflowTransition** `id, workflowId, fromStageId, toStageId, action, label,
  requiredPermission, requiresReason, forward (sujeita a exitRequirements), systemOnly`.
- **DemandStageHistory** `demandId, stageCode, category, enteredAt, exitedAt, action, actorId`.

### Approval
- **ApprovalRule** `id, workflowId, stageCode, name, approverRole, conditionField?,
  conditionOperator? (EQ, NEQ, GT, GTE, LT, LTE, IN), conditionValue?, projectId?, active`.
  Condição nula = sempre exige. Campos suportados: `priority, estimatedBudget,
  impactLevel, urgency, demandType, project, regulatoryRequirement, hasBudgetImpact`.
- **Approval** `id, demandId, ruleId, stageCode, approverRole, status (PENDING,
  APPROVED, REJECTED, CANCELLED), decidedBy, decidedAt, comment`.

### Documentos e comunicação
- **Document** `id, demandId, kind (DOCUMENT, PRESENTATION, ATTACHMENT, TECHNICAL_DOC,
  CLIENT_DOC), fileName, contentType, sizeBytes, storageKey, sha256, extractedText,
  extractionStatus (PENDING, EXTRACTED, FAILED, NOT_APPLICABLE), uploadedBy, uploadedAt`.
- **InformationRequest** `id, demandId, question, requestedBy, requestedAt, response,
  respondedBy, respondedAt, status (OPEN, ANSWERED, CANCELLED)`.
- **Comment** `id, demandId, authorId?, authorName, body, visibility (PUBLIC, INTERNAL),
  source (PLATFORM, JIRA, GITLAB, SYSTEM), externalId, createdAt`.
- **ChatMessage** `id, demandId, role (USER, ASSISTANT), content, agentRunId, createdAt`.

### IA
- **AiAgentRun** `id, demandId?, agent, provider, model, mode (REAL, MOCK), input (JSON),
  output (JSON), status (SUCCESS, FAILED, INVALID_OUTPUT), error, durationMs, createdBy, createdAt`.
- **AiSuggestion** `id, demandId, agentRunId, agent, kind (FIELD_VALUE, MISSING_INFO,
  INCONSISTENCY, DUPLICATE, REFINEMENT_ITEM), field, suggestedValue, alternativeValue,
  confidence (0..1), sourceType (FORM, DOCUMENT, PRESENTATION, CHAT, ANALYSIS),
  sourceRef, rationale, status (PENDING, ACCEPTED, EDITED, REJECTED, SUPERSEDED),
  finalValue, decidedBy, decidedAt, decisionReason`.
- **AiAnalysis** `id, demandId, agentRunId, kind (TRIAGE, ARCHITECTURE, TECH_SPEC,
  TECH_PROMPT, SQUAD_PLAN, PROGRESS, TECHNICAL_DOC, CLIENT_DOC), content (JSON/Markdown),
  editedContent, editedBy, editedAt, createdAt`. A versão exibida é `editedContent ?? content`.

### Refinamento
- **Meeting** `id, demandId, title, heldAt, participants, notes, createdBy`.
- **Decision** `id, demandId, meetingId?, type (BUSINESS, ARCHITECTURAL, TECHNICAL),
  description, rationale, decidedBy, createdAt`.
- **RefinementItem** `id, demandId, type (FUNCTIONAL_REQUIREMENT,
  NON_FUNCTIONAL_REQUIREMENT, ACCEPTANCE_CRITERION, DEPENDENCY, RISK, QUESTION,
  PENDING_ITEM), description, status (OPEN, RESOLVED), origin (HUMAN, AI_ACCEPTED),
  createdBy, createdAt`.

### Execução e integrações
- **ExecutionStatus** `code, name, position, gitlabLabel, done, jiraCommentTemplate`.
  Seed: TODO, DEVELOPMENT, CODE_REVIEW, QA, DEPLOY, DONE.
- **TechnicalExecution** `id, demandId, system (GITLAB), strategy (TECHNICAL_PROMPT,
  AGENT_SQUAD), statusCode, externalLinkId, startedAt, completedAt, lastEventAt`.
- **ExecutionEvent** `id, executionId, fromStatus, toStatus, summary, source, receivedAt`.
- **ExternalLink** `id, demandId, system (JIRA, GITLAB), mode (REAL, MOCK), externalId,
  externalKey, url, syncStatus (OK, FAILED), lastError, lastSyncAt`.
- **WebhookEvent** `id, system, eventType, externalId, processed, error, receivedAt`.

### Notificação e auditoria
- **Notification** `id, recipientId, channel (IN_APP, EMAIL, TEAMS), eventType,
  subject, body, demandId, status (SENT, FAILED, SKIPPED), error, createdAt, readAt`.
- **AuditLog** `id, actorId?, actorName, actorRoles, action, entityType, entityId,
  demandId, field, beforeValue, afterValue, reason, aiSuggestionId, metadata, createdAt`.
  `actorName = "SYSTEM"` ou `"AI:<agente>"` para ações automáticas.

## 3. Legado

Demandas importadas: `source = LEGACY`, `lifecycleState = LEGACY`, `readOnly = true`,
sem workflow/estágio, `original*` preenchidos com o que existir. Nenhum histórico é
recriado; campos ausentes permanecem nulos. Participam da detecção de duplicidade.
