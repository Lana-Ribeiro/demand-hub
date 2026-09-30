package com.demandhub.platform.approval.service;

import com.demandhub.platform.approval.domain.Approval;
import com.demandhub.platform.approval.domain.ApprovalRule;
import com.demandhub.platform.approval.repository.ApprovalRepositories.ApprovalRepository;
import com.demandhub.platform.approval.repository.ApprovalRepositories.ApprovalRuleRepository;
import com.demandhub.platform.audit.service.AuditService;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.repository.DemandRepositories.DemandRepository;
import com.demandhub.platform.identity.repository.UserRepository;
import com.demandhub.platform.shared.error.ApiException;
import com.demandhub.platform.shared.events.DomainEvents.ApprovalDecided;
import com.demandhub.platform.shared.events.DomainEvents.ApprovalRequested;
import com.demandhub.platform.shared.security.CurrentUser;
import com.demandhub.platform.shared.security.Permissions;
import com.demandhub.platform.workflow.domain.WorkflowStage;
import com.demandhub.platform.workflow.service.ExitRequirementChecker;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Approval gates: cria aprovações a partir de regras configuráveis e registra decisões humanas. */
@Service
public class ApprovalService implements ExitRequirementChecker {

    public static final String REQUIREMENT_CODE = "APPROVALS_GRANTED";

    private final ApprovalRuleRepository rules;
    private final ApprovalRepository approvals;
    private final DemandRepository demands;
    private final UserRepository users;
    private final RuleConditionEvaluator evaluator;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ApprovalService(ApprovalRuleRepository rules, ApprovalRepository approvals, DemandRepository demands,
                           UserRepository users, RuleConditionEvaluator evaluator, AuditService audit,
                           ApplicationEventPublisher events, Clock clock) {
        this.rules = rules;
        this.approvals = approvals;
        this.demands = demands;
        this.users = users;
        this.evaluator = evaluator;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    /** Avalia as regras do estágio e cria uma aprovação PENDING por papel aprovador. */
    @Transactional
    public List<Approval> evaluateOnEnter(Demand demand, WorkflowStage stage) {
        List<ApprovalRule> matched = rules
                .findByWorkflowIdAndStageCodeAndActiveTrueOrderByPositionAsc(stage.getWorkflow().getId(), stage.getCode())
                .stream().filter(r -> evaluator.matches(r, demand)).toList();
        Map<String, List<ApprovalRule>> byRole = matched.stream()
                .collect(Collectors.groupingBy(ApprovalRule::getApproverRole, LinkedHashMap::new, Collectors.toList()));
        List<Approval> created = new ArrayList<>();
        byRole.forEach((role, roleRules) -> {
            Approval a = new Approval();
            a.setDemandId(demand.getId());
            a.setRuleId(roleRules.get(0).getId());
            a.setRuleName(roleRules.stream().map(ApprovalRule::getName).collect(Collectors.joining(" · ")));
            a.setStageCode(stage.getCode());
            a.setApproverRole(role);
            a.setCreatedAt(clock.instant());
            approvals.save(a);
            created.add(a);
            audit.event("APPROVAL_REQUESTED").actor(AuditService.SYSTEM).entity("Approval", a.getId())
                    .demand(demand.getId()).change("approverRole", null, role).reason(a.getRuleName()).record();
            events.publishEvent(new ApprovalRequested(demand.getId(), a.getId(), role, a.getRuleName()));
        });
        return created;
    }

    @Transactional
    public void cancelPending(UUID demandId, String stageCode) {
        approvals.findByDemandIdAndStageCodeAndStatusIn(demandId, stageCode, EnumSet.of(Approval.Status.PENDING))
                .forEach(a -> {
                    a.setStatus(Approval.Status.CANCELLED);
                    a.setDecidedAt(clock.instant());
                });
    }

    @Transactional
    public Approval decide(UUID approvalId, boolean approve, String comment, CurrentUser user) {
        Approval approval = approvals.findById(approvalId).orElseThrow(() -> ApiException.notFound("Aprovação", approvalId));
        if (!user.has(Permissions.APPROVAL_DECIDE) || !user.hasRole(approval.getApproverRole())) {
            throw ApiException.forbidden("Somente usuários com papel " + approval.getApproverRole() + " podem decidir esta aprovação.");
        }
        if (approval.getStatus() != Approval.Status.PENDING) {
            throw ApiException.businessRule("APPROVAL_NOT_PENDING", "Esta aprovação já foi decidida ou cancelada.");
        }
        Demand demand = demands.findById(approval.getDemandId())
                .orElseThrow(() -> ApiException.notFound("Demanda", approval.getDemandId()));
        if (demand.getCurrentStage() == null || !approval.getStageCode().equals(demand.getCurrentStage().getCode())) {
            throw ApiException.businessRule("APPROVAL_STAGE_MISMATCH", "A demanda não está mais no estágio desta aprovação.");
        }
        if (!approve && (comment == null || comment.isBlank())) {
            throw ApiException.businessRule("REASON_REQUIRED", "Informe o motivo da rejeição.");
        }
        approval.setStatus(approve ? Approval.Status.APPROVED : Approval.Status.REJECTED);
        approval.setDecidedBy(users.getReferenceById(user.id()));
        approval.setDecidedAt(clock.instant());
        approval.setComment(comment);
        audit.event(approve ? "APPROVAL_GRANTED" : "APPROVAL_REJECTED").entity("Approval", approval.getId())
                .demand(demand.getId()).change("status", "PENDING", approval.getStatus().name())
                .reason(comment).metadata("role=" + approval.getApproverRole() + "; rule=" + approval.getRuleName()).record();
        events.publishEvent(new ApprovalDecided(demand.getId(), approval.getId(), approval.getStageCode(), approve, comment));
        return approval;
    }

    @Transactional(readOnly = true)
    public List<Approval> forDemand(UUID demandId) {
        return approvals.findByDemandIdOrderByCreatedAtAsc(demandId);
    }

    @Transactional(readOnly = true)
    public List<Approval> pendingFor(CurrentUser user) {
        if (!user.has(Permissions.APPROVAL_DECIDE)) {
            return List.of();
        }
        return approvals.findByStatusAndApproverRoleInOrderByCreatedAtAsc(Approval.Status.PENDING, user.roles());
    }

    @Override
    public String code() {
        return REQUIREMENT_CODE;
    }

    @Override
    public Optional<String> unmetReason(Demand demand) {
        if (demand.getCurrentStage() == null) {
            return Optional.empty();
        }
        List<Approval> blocking = approvals.findByDemandIdAndStageCodeAndStatusIn(demand.getId(),
                demand.getCurrentStage().getCode(), EnumSet.of(Approval.Status.PENDING, Approval.Status.REJECTED));
        if (blocking.isEmpty()) {
            return Optional.empty();
        }
        String roles = blocking.stream().map(a -> a.getApproverRole() + " (" + a.getStatus().name() + ")")
                .collect(Collectors.joining(", "));
        return Optional.of("Aprovações obrigatórias não concluídas: " + roles);
    }
}
