# Workflow

## 1. Conceitos

- O **workflow é dado**, não código: `workflow_definitions`, `workflow_stages`,
  `workflow_transitions`, `approval_rules`. O `WorkflowEngine` é genérico.
- O **tipo da demanda** aponta para o workflow. Alterar o tipo durante a triagem
  troca o workflow mantendo o estágio de mesmo código (todos os workflows possuem
  `PMO_TRIAGE`); se não existir, a troca é bloqueada.
- Toda transição passa pelo engine, que valida, em ordem:
  1. Demanda não é `readOnly`.
  2. Transição existe a partir do estágio atual para a `action` solicitada.
  3. Ator possui `requiredPermission` (transições `systemOnly` só pelo sistema).
  4. Sem `DEMAND_VIEW_ALL`, o ator precisa ser o solicitante.
  5. `requiresReason` → motivo obrigatório.
  6. Transições `forward` exigem todos os `exitRequirements` do estágio atual.
- Ao entrar em um estágio o engine: registra histórico; cancela aprovações pendentes do
  estágio anterior; avalia `ApprovalRule`s do novo estágio e cria `Approval`s
  PENDING; publica `DemandStageChanged`; executa `onEnterActions` (após commit); e, se
  `autoAdvance` e requisitos atendidos, avança pela transição forward única.

## 2. Requisitos de saída (gates)

| Código | Regra determinística |
|---|---|
| `APPROVALS_GRANTED` | Nenhuma aprovação do estágio PENDING ou REJECTED |
| `TRIAGE_FIELDS_SET` | Projeto, tipo e prioridade definidos |
| `INFO_REQUESTS_ANSWERED` | Nenhuma pendência OPEN |
| `REFINEMENT_COMPLETE` | ≥1 critério de aceite, ≥1 requisito funcional, nenhuma QUESTION/PENDING_ITEM aberta |
| `EXECUTION_DONE` | Execução técnica em status `done` (vindo do GitLab) |
| `DOCUMENTATION_GENERATED` | Existe documentação técnica e para cliente |

Novos gates = nova implementação de `ExitRequirementChecker` (Strategy).

## 3. Workflow TECHNICAL (Desenvolvimento, Melhoria, Automação, Integração)

```text
DRAFT ──SUBMIT──► AI_ANALYSIS ──(sistema: análise concluída)──► PMO_TRIAGE
PMO_TRIAGE ──REQUEST_INFO──► INFO_REQUESTED ──RESPOND (solicitante)──► PMO_TRIAGE
PMO_TRIAGE ──REJECT──► REJECTED
PMO_TRIAGE ──APPROVE [TRIAGE_FIELDS_SET, INFO_REQUESTS_ANSWERED]──► EXECUTIVE_APPROVAL
   onEnter: CREATE_JIRA_ISSUE · regras: DIRECTOR se P1 / orçamento > 500k / regulatório
   autoAdvance quando não há aprovação pendente
EXECUTIVE_APPROVAL ──ADVANCE [APPROVALS_GRANTED]──► REFINEMENT
EXECUTIVE_APPROVAL ──REJECT──► REJECTED
REFINEMENT ──ADVANCE [REFINEMENT_COMPLETE]──► ARCHITECTURE_REVIEW
ARCHITECTURE_REVIEW (regra: ARCHITECT sempre, autoAdvance) ──ADVANCE [APPROVALS_GRANTED]──► READY_FOR_DEVELOPMENT
ARCHITECTURE_REVIEW ──RETURN_TO_REFINEMENT──► REFINEMENT
READY_FOR_DEVELOPMENT (onEnter: CREATE_SCM_ISSUE) ──START_EXECUTION──► IN_DEVELOPMENT
IN_DEVELOPMENT ──ADVANCE [EXECUTION_DONE]──► DOCUMENTATION
DOCUMENTATION ──COMPLETE [DOCUMENTATION_GENERATED]──► COMPLETED
Qualquer estágio ativo ──CANCEL (motivo)──► CANCELLED
```

Mapeamento Jira (`jiraStatus`): AI_ANALYSIS/PMO_TRIAGE → "Triagem";
EXECUTIVE_APPROVAL → "Aprovação"; REFINEMENT → "Refinamento";
ARCHITECTURE_REVIEW → "Arquitetura"; READY/IN_DEVELOPMENT → "Em desenvolvimento";
DOCUMENTATION → "Documentação"; COMPLETED → "Concluído". Os nomes são configuráveis.

## 4. Workflow OPERATIONAL (Alocação de recurso, Mudança operacional)

```text
DRAFT → AI_ANALYSIS → PMO_TRIAGE (⇄ INFO_REQUESTED, → REJECTED)
PMO_TRIAGE ──APPROVE──► CAPACITY_ANALYSIS (onEnter: CREATE_JIRA_ISSUE)
CAPACITY_ANALYSIS ──ADVANCE──► MANAGER_APPROVAL (regra: MANAGER sempre; DIRECTOR se orçamento > 500k; autoAdvance)
MANAGER_APPROVAL ──ADVANCE [APPROVALS_GRANTED]──► OPERATIONAL_EXECUTION
OPERATIONAL_EXECUTION ──COMPLETE──► COMPLETED
```

**Sem GitLab**, sem estágio técnico — atende ao exemplo "Demanda → PMO → Análise de
capacidade → Gestor → Alocação de recurso → Conclusão".

## 5. Workflow SERVICE (Consultoria, Suporte, Outro)

```text
DRAFT → AI_ANALYSIS → PMO_TRIAGE (⇄ INFO_REQUESTED, → REJECTED)
PMO_TRIAGE ──APPROVE──► EXECUTIVE_APPROVAL (CREATE_JIRA_ISSUE; DIRECTOR se P1; autoAdvance)
EXECUTIVE_APPROVAL ──ADVANCE──► IN_EXECUTION ──COMPLETE──► COMPLETED
```

## 6. Separação Demand Lifecycle × Technical Execution Lifecycle

```text
Ciclo da Demanda (Jira PMO / plataforma)     Execução Técnica (GitLab)
Status: Em desenvolvimento                   Status: QA   Sistema: GitLab
```

- O estágio da demanda **só muda por transição do workflow**.
- Eventos do GitLab alteram apenas `TechnicalExecution.statusCode` e geram
  `ExecutionEvent` + comentário no Jira usando o template do status, ex.:
  *"Execução técnica avançou para QA. Implementação concluída e aguardando validação."*
- Quando a execução chega a um status `done`, o gate `EXECUTION_DONE` é satisfeito e
  o PMO pode avançar para `DOCUMENTATION` (decisão humana, registrada).

## 7. Estados da execução técnica (configuráveis)

`TODO → DEVELOPMENT → CODE_REVIEW → QA → DEPLOY → DONE`, mapeados para labels
escopadas do GitLab (`status::todo`, `status::development`, ...). Tabela
`execution_statuses`.

## 8. Ciclo de vida derivado (`lifecycleState`)

| Categoria do estágio | lifecycleState |
|---|---|
| DRAFT | DRAFT |
| ON_HOLD | ON_HOLD |
| DONE | COMPLETED |
| REJECTED | REJECTED |
| CANCELLED | CANCELLED |
| demais | ACTIVE |
