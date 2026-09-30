package com.demandhub.platform.workflow.web;

import com.demandhub.platform.approval.domain.ApprovalRule;
import com.demandhub.platform.approval.domain.ConditionOperator;
import com.demandhub.platform.approval.repository.ApprovalRepositories.ApprovalRuleRepository;
import com.demandhub.platform.approval.service.RuleConditionEvaluator;
import com.demandhub.platform.audit.service.AuditService;
import com.demandhub.platform.execution.domain.ExecutionStatus;
import com.demandhub.platform.execution.repository.ExecutionRepositories.ExecutionStatusRepository;
import com.demandhub.platform.identity.repository.RoleRepository;
import com.demandhub.platform.shared.error.ApiException;
import com.demandhub.platform.workflow.domain.StageAction;
import com.demandhub.platform.workflow.domain.WorkflowDefinition;
import com.demandhub.platform.workflow.domain.WorkflowStage;
import com.demandhub.platform.workflow.repository.WorkflowRepositories.WorkflowDefinitionRepository;
import com.demandhub.platform.workflow.repository.WorkflowRepositories.WorkflowStageRepository;
import com.demandhub.platform.workflow.repository.WorkflowRepositories.WorkflowTransitionRepository;
import com.demandhub.platform.workflow.service.ExitRequirementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Configuração de workflows, approval gates e status técnicos — sem deploy, com auditoria. */
@RestController
@RequestMapping("/api/admin")
public class WorkflowAdminController {

    private final WorkflowDefinitionRepository workflows;
    private final WorkflowStageRepository stages;
    private final WorkflowTransitionRepository transitions;
    private final ApprovalRuleRepository rules;
    private final ExecutionStatusRepository executionStatuses;
    private final RoleRepository roles;
    private final ExitRequirementService exitRequirements;
    private final AuditService audit;

    public WorkflowAdminController(WorkflowDefinitionRepository workflows, WorkflowStageRepository stages,
                                   WorkflowTransitionRepository transitions, ApprovalRuleRepository rules,
                                   ExecutionStatusRepository executionStatuses, RoleRepository roles,
                                   ExitRequirementService exitRequirements, AuditService audit) {
        this.workflows = workflows;
        this.stages = stages;
        this.transitions = transitions;
        this.rules = rules;
        this.executionStatuses = executionStatuses;
        this.roles = roles;
        this.exitRequirements = exitRequirements;
        this.audit = audit;
    }

    @GetMapping("/workflows")
    @PreAuthorize("hasAnyAuthority('ADMIN_CONFIG','DEMAND_TRIAGE')")
    @Transactional(readOnly = true)
    public List<WorkflowView> list() {
        return workflows.findAll().stream().map(this::view).toList();
    }

    @GetMapping("/workflow-metadata")
    @PreAuthorize("hasAnyAuthority('ADMIN_CONFIG','DEMAND_TRIAGE')")
    public Map<String, Object> metadata() {
        return Map.of("exitRequirements", exitRequirements.knownCodes(),
                "stageActions", Arrays.stream(StageAction.values()).map(Enum::name).toList(),
                "conditionFields", RuleConditionEvaluator.supportedFields().stream().sorted().toList(),
                "operators", Arrays.stream(ConditionOperator.values()).map(Enum::name).toList(),
                "roles", roles.findAll().stream().map(r -> r.getCode()).sorted().toList());
    }

    @PutMapping("/workflows/stages/{id}")
    @PreAuthorize("hasAuthority('ADMIN_CONFIG')")
    @Transactional
    public StageView updateStage(@PathVariable Long id, @Valid @RequestBody StageRequest req) {
        WorkflowStage s = stages.findById(id).orElseThrow(() -> ApiException.notFound("Estágio", id));
        req.exitRequirements().forEach(code -> {
            if (!exitRequirements.isKnown(code)) throw ApiException.badRequest("UNKNOWN_REQUIREMENT", "Requisito desconhecido: " + code);
        });
        req.onEnterActions().forEach(StageAction::valueOf);
        String before = describe(s);
        s.setName(req.name());
        s.setAutoAdvance(req.autoAdvance());
        s.setJiraStatus(req.jiraStatus());
        s.setExitRequirements(String.join(",", req.exitRequirements()));
        s.setOnEnterActions(String.join(",", req.onEnterActions()));
        audit.event("WORKFLOW_STAGE_UPDATED").entity("WorkflowStage", id).change("stage", before, describe(s)).record();
        return StageView.from(s);
    }

    @PostMapping("/approval-rules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ADMIN_CONFIG')")
    @Transactional
    public ApprovalRule createRule(@Valid @RequestBody RuleRequest req) {
        ApprovalRule r = new ApprovalRule();
        apply(r, req);
        rules.save(r);
        audit.event("APPROVAL_RULE_CREATED").entity("ApprovalRule", r.getId()).change("rule", null, describe(r)).record();
        return r;
    }

    @PutMapping("/approval-rules/{id}")
    @PreAuthorize("hasAuthority('ADMIN_CONFIG')")
    @Transactional
    public ApprovalRule updateRule(@PathVariable Long id, @Valid @RequestBody RuleRequest req) {
        ApprovalRule r = rules.findById(id).orElseThrow(() -> ApiException.notFound("Regra", id));
        String before = describe(r);
        apply(r, req);
        audit.event("APPROVAL_RULE_UPDATED").entity("ApprovalRule", id).change("rule", before, describe(r)).record();
        return r;
    }

    @DeleteMapping("/approval-rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ADMIN_CONFIG')")
    @Transactional
    public void deactivateRule(@PathVariable Long id) {
        ApprovalRule r = rules.findById(id).orElseThrow(() -> ApiException.notFound("Regra", id));
        r.setActive(false);
        audit.event("APPROVAL_RULE_DEACTIVATED").entity("ApprovalRule", id).change("rule", describe(r), null).record();
    }

    @GetMapping("/execution-statuses")
    @PreAuthorize("hasAnyAuthority('ADMIN_CONFIG','EXECUTION_VIEW')")
    public List<ExecutionStatus> executionStatuses() {
        return executionStatuses.findAllByOrderByPositionAsc();
    }

    @PutMapping("/execution-statuses/{code}")
    @PreAuthorize("hasAuthority('ADMIN_CONFIG')")
    @Transactional
    public ExecutionStatus updateExecutionStatus(@PathVariable String code, @Valid @RequestBody ExecutionStatusRequest req) {
        ExecutionStatus s = executionStatuses.findById(code).orElseThrow(() -> ApiException.notFound("Status técnico", code));
        String before = s.getName() + "|" + s.getScmLabel() + "|" + s.getJiraCommentTemplate();
        s.setName(req.name());
        s.setScmLabel(req.scmLabel());
        s.setJiraCommentTemplate(req.jiraCommentTemplate());
        audit.event("EXECUTION_STATUS_UPDATED").entity("ExecutionStatus", code)
                .change("status", before, s.getName() + "|" + s.getScmLabel() + "|" + s.getJiraCommentTemplate()).record();
        return s;
    }

    private void apply(ApprovalRule r, RuleRequest req) {
        WorkflowDefinition wf = workflows.findById(req.workflowId()).orElseThrow(() -> ApiException.notFound("Workflow", req.workflowId()));
        if (wf.stage(req.stageCode()).isEmpty()) {
            throw ApiException.badRequest("INVALID_STAGE", "Estágio inexistente no workflow: " + req.stageCode());
        }
        if (!roles.existsById(req.approverRole())) {
            throw ApiException.badRequest("INVALID_ROLE", "Papel inexistente: " + req.approverRole());
        }
        boolean hasField = req.conditionField() != null && !req.conditionField().isBlank();
        if (hasField && (!RuleConditionEvaluator.supportedFields().contains(req.conditionField()) || req.conditionOperator() == null)) {
            throw ApiException.badRequest("INVALID_CONDITION", "Condição inválida: campo suportado e operador são obrigatórios.");
        }
        r.setWorkflowId(wf.getId());
        r.setStageCode(req.stageCode());
        r.setName(req.name());
        r.setApproverRole(req.approverRole());
        r.setConditionField(hasField ? req.conditionField() : null);
        r.setConditionOperator(hasField ? req.conditionOperator() : null);
        r.setConditionValue(hasField ? req.conditionValue() : null);
        r.setProjectId(req.projectId());
        r.setActive(req.active() == null || req.active());
    }

    private WorkflowView view(WorkflowDefinition wf) {
        return new WorkflowView(wf.getId(), wf.getCode(), wf.getName(), wf.getDescription(),
                stages.findByWorkflowIdOrderByPositionAsc(wf.getId()).stream().map(StageView::from).toList(),
                transitions.findByWorkflowId(wf.getId()).stream().map(t -> new TransitionView(t.getId(), t.getFromStage().getCode(),
                        t.getToStage().getCode(), t.getAction(), t.getLabel(), t.getRequiredPermission(), t.isRequiresReason(),
                        t.isForward(), t.isSystemOnly())).toList(),
                rules.findByWorkflowIdOrderByStageCodeAscPositionAsc(wf.getId()));
    }

    private static String describe(WorkflowStage s) {
        return "%s|auto=%s|jira=%s|exit=%s|onEnter=%s".formatted(s.getName(), s.isAutoAdvance(), s.getJiraStatus(),
                s.getExitRequirements(), s.getOnEnterActions());
    }

    private static String describe(ApprovalRule r) {
        return "%s@%s → %s se %s %s %s (ativo=%s)".formatted(r.getName(), r.getStageCode(), r.getApproverRole(),
                r.getConditionField(), r.getConditionOperator(), r.getConditionValue(), r.isActive());
    }

    public record StageView(Long id, String code, String name, String category, int position, boolean autoAdvance,
                            String jiraStatus, List<String> onEnterActions, List<String> exitRequirements) {
        static StageView from(WorkflowStage s) {
            return new StageView(s.getId(), s.getCode(), s.getName(), s.getCategory().name(), s.getPosition(), s.isAutoAdvance(),
                    s.getJiraStatus(), s.onEnterActionSet().stream().map(Enum::name).toList(), List.copyOf(s.exitRequirementSet()));
        }
    }

    public record TransitionView(Long id, String from, String to, String action, String label, String requiredPermission,
                                 boolean requiresReason, boolean forward, boolean systemOnly) {}

    public record WorkflowView(Long id, String code, String name, String description, List<StageView> stages,
                               List<TransitionView> transitions, List<ApprovalRule> approvalRules) {}

    public record StageRequest(@NotBlank @Size(max = 200) String name, boolean autoAdvance, @Size(max = 100) String jiraStatus,
                               @NotNull List<String> exitRequirements, @NotNull List<String> onEnterActions) {}

    public record RuleRequest(@NotNull Long workflowId, @NotBlank String stageCode, @NotBlank @Size(max = 200) String name,
                              @NotBlank String approverRole, String conditionField, ConditionOperator conditionOperator,
                              @Size(max = 500) String conditionValue, Long projectId, Boolean active) {}

    public record ExecutionStatusRequest(@NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 100) String scmLabel,
                                         @NotBlank @Size(max = 1000) String jiraCommentTemplate) {}
}
