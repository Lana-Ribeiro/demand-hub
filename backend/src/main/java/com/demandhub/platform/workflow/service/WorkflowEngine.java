package com.demandhub.platform.workflow.service;

import com.demandhub.platform.approval.service.ApprovalService;
import com.demandhub.platform.audit.service.AuditService;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.domain.DemandStageHistory;
import com.demandhub.platform.demand.repository.DemandRepositories.DemandStageHistoryRepository;
import com.demandhub.platform.shared.error.ApiException;
import com.demandhub.platform.shared.events.DomainEvents.DemandStageChanged;
import com.demandhub.platform.shared.security.CurrentUser;
import com.demandhub.platform.shared.security.Permissions;
import com.demandhub.platform.workflow.domain.StageCategory;
import com.demandhub.platform.workflow.domain.WorkflowDefinition;
import com.demandhub.platform.workflow.domain.WorkflowStage;
import com.demandhub.platform.workflow.domain.WorkflowTransition;
import com.demandhub.platform.workflow.repository.WorkflowRepositories.WorkflowStageRepository;
import com.demandhub.platform.workflow.repository.WorkflowRepositories.WorkflowTransitionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Motor de workflow genérico e determinístico. É o ÚNICO componente que altera o estágio de uma demanda.
 * Toda regra vem de configuração (estágios, transições, gates, regras de aprovação).
 */
@Service
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);
    private static final int MAX_AUTO_ADVANCE_DEPTH = 10;

    private final WorkflowTransitionRepository transitions;
    private final WorkflowStageRepository stages;
    private final DemandStageHistoryRepository history;
    private final ExitRequirementService exitRequirements;
    private final ApprovalService approvals;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public WorkflowEngine(WorkflowTransitionRepository transitions, WorkflowStageRepository stages,
                          DemandStageHistoryRepository history, ExitRequirementService exitRequirements,
                          ApprovalService approvals, AuditService audit, ApplicationEventPublisher events, Clock clock) {
        this.transitions = transitions;
        this.stages = stages;
        this.history = history;
        this.exitRequirements = exitRequirements;
        this.approvals = approvals;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    /** Posiciona uma nova demanda no estágio inicial (DRAFT) do workflow. */
    @Transactional
    public void start(Demand demand, WorkflowDefinition workflow, CurrentUser actor) {
        WorkflowStage initial = workflow.initialStage()
                .orElseThrow(() -> new IllegalStateException("Workflow " + workflow.getCode() + " sem estágio DRAFT"));
        demand.setWorkflow(workflow);
        enterStage(demand, null, initial, "CREATE", null, actor);
    }

    /** Transição disparada por usuário. */
    @Transactional
    public Demand transition(Demand demand, String action, String reason, CurrentUser actor) {
        WorkflowTransition t = findTransition(demand, action);
        if (t.isSystemOnly()) {
            throw ApiException.forbidden("A ação '" + action + "' é executada somente pelo sistema.");
        }
        if (t.getRequiredPermission() != null && !actor.has(t.getRequiredPermission())) {
            throw ApiException.forbidden("Você não possui permissão para: " + t.getLabel());
        }
        if (!actor.has(Permissions.DEMAND_VIEW_ALL) && !demand.isOwnedBy(actor.id())) {
            throw ApiException.forbidden("Você só pode agir sobre as próprias demandas.");
        }
        apply(demand, t, reason, actor);
        return demand;
    }

    /** Transição disparada pelo sistema (fim da análise, aprovação rejeitada, auto-avanço). */
    @Transactional
    public boolean systemTransition(Demand demand, String action, String reason) {
        Optional<WorkflowTransition> t = findOptional(demand, action);
        if (t.isEmpty()) {
            return false;
        }
        apply(demand, t.get(), reason, null);
        return true;
    }

    /** Avança automaticamente se o estágio atual é autoAdvance e todos os gates estão cumpridos. */
    @Transactional
    public boolean tryAutoAdvance(Demand demand) {
        return tryAutoAdvance(demand, 0);
    }

    private boolean tryAutoAdvance(Demand demand, int depth) {
        WorkflowStage stage = demand.getCurrentStage();
        if (stage == null || !stage.isAutoAdvance() || depth > MAX_AUTO_ADVANCE_DEPTH) {
            return false;
        }
        List<WorkflowTransition> forward = transitions.findFromStage(stage.getId()).stream()
                .filter(WorkflowTransition::isForward).toList();
        if (forward.size() != 1 || !exitRequirements.unmet(demand, stage).isEmpty()) {
            return false;
        }
        applyInternal(demand, forward.get(0), "Avanço automático: requisitos do estágio cumpridos.", null, depth + 1);
        return true;
    }

    /** Transições disponíveis a partir do estágio atual, com indicação de bloqueio (para a UI). */
    @Transactional(readOnly = true)
    public List<AvailableTransition> available(Demand demand, CurrentUser user) {
        if (demand.isReadOnly() || demand.getCurrentStage() == null) {
            return List.of();
        }
        WorkflowStage stage = demand.getCurrentStage();
        List<String> unmet = exitRequirements.unmet(demand, stage);
        boolean ownerOrAll = user.has(Permissions.DEMAND_VIEW_ALL) || demand.isOwnedBy(user.id());
        return transitions.findFromStage(stage.getId()).stream()
                .filter(t -> !t.isSystemOnly())
                .filter(t -> ownerOrAll && (t.getRequiredPermission() == null || user.has(t.getRequiredPermission())))
                .map(t -> new AvailableTransition(t.getAction(), t.getLabel(), t.getToStage().getCode(),
                        t.getToStage().getName(), t.isRequiresReason(), t.isForward(),
                        t.isForward() ? unmet : List.of()))
                .toList();
    }

    /** Troca o workflow (mudança de tipo na triagem), mantendo o estágio de mesmo código. */
    @Transactional
    public void changeWorkflow(Demand demand, WorkflowDefinition target, CurrentUser actor) {
        if (demand.getWorkflow() != null && demand.getWorkflow().getId().equals(target.getId())) {
            return;
        }
        String currentCode = demand.getCurrentStage().getCode();
        WorkflowStage equivalent = stages.findByWorkflowIdAndCode(target.getId(), currentCode)
                .orElseThrow(() -> ApiException.businessRule("WORKFLOW_CHANGE_NOT_ALLOWED",
                        "O workflow '" + target.getName() + "' não possui o estágio " + currentCode + "; troca de tipo não permitida neste momento."));
        String before = demand.getWorkflow() == null ? null : demand.getWorkflow().getCode();
        demand.setWorkflow(target);
        demand.setCurrentStage(equivalent);
        audit.event("WORKFLOW_CHANGED").entity("Demand", demand.getId()).demand(demand.getId())
                .change("workflow", before, target.getCode()).record();
    }

    private void apply(Demand demand, WorkflowTransition t, String reason, CurrentUser actor) {
        applyInternal(demand, t, reason, actor, 0);
    }

    private void applyInternal(Demand demand, WorkflowTransition t, String reason, CurrentUser actor, int depth) {
        if (demand.isReadOnly()) {
            throw ApiException.businessRule("DEMAND_READ_ONLY", "Demanda somente leitura (legado ou encerrada).");
        }
        if (t.isRequiresReason() && (reason == null || reason.isBlank())) {
            throw ApiException.businessRule("REASON_REQUIRED", "Informe o motivo para: " + t.getLabel());
        }
        if (t.isForward()) {
            List<String> unmet = exitRequirements.unmet(demand, demand.getCurrentStage());
            if (!unmet.isEmpty()) {
                throw ApiException.businessRule("GATE_BLOCKED", "Avanço bloqueado: requisitos obrigatórios pendentes.",
                        Map.of("unmetRequirements", unmet));
            }
        }
        WorkflowStage from = demand.getCurrentStage();
        enterStage(demand, from, t.getToStage(), t.getAction(), reason, actor);
        tryAutoAdvance(demand, depth);
    }

    private void enterStage(Demand demand, WorkflowStage from, WorkflowStage to, String action, String reason, CurrentUser actor) {
        Instant now = clock.instant();
        if (demand.getId() != null) {
            history.findFirstByDemandIdAndExitedAtIsNullOrderByEnteredAtDesc(demand.getId())
                    .ifPresent(h -> h.setExitedAt(now));
        }
        demand.setCurrentStage(to);
        demand.setStageEnteredAt(now);
        demand.setLifecycleState(to.getCategory().lifecycleState());
        if (to.getCategory() == StageCategory.DONE) {
            demand.setCompletedAt(now);
        }
        if (to.getCategory().isTerminal()) {
            demand.setReadOnly(true);
        }

        DemandStageHistory h = new DemandStageHistory();
        h.setDemandId(demand.getId());
        h.setStageCode(to.getCode());
        h.setStageName(to.getName());
        h.setCategory(to.getCategory());
        h.setAction(action);
        h.setActorId(actor == null ? null : actor.id());
        h.setActorName(actor == null ? AuditService.SYSTEM : actor.fullName());
        h.setReason(reason);
        h.setEnteredAt(now);
        history.save(h);

        if (from != null) {
            approvals.cancelPending(demand.getId(), from.getCode());
            AuditService.Entry entry = audit.event("STAGE_CHANGED").entity("Demand", demand.getId()).demand(demand.getId())
                    .change("stage", from.getCode(), to.getCode()).reason(reason).metadata("action=" + action);
            if (actor == null) {
                entry.actor(AuditService.SYSTEM);
            }
            entry.record();
        }
        approvals.evaluateOnEnter(demand, to);
        log.info("Demanda {} -> estágio {} (ação {})", demand.displayId(), to.getCode(), action);

        events.publishEvent(new DemandStageChanged(demand.getId(), from == null ? null : from.getCode(), to.getCode(),
                to.getName(), to.getCategory(), action, actor == null ? AuditService.SYSTEM : actor.fullName(), reason,
                to.onEnterActionSet()));
    }

    private WorkflowTransition findTransition(Demand demand, String action) {
        return findOptional(demand, action).orElseThrow(() -> ApiException.businessRule("TRANSITION_NOT_ALLOWED",
                "A ação '" + action + "' não é permitida no estágio atual"
                        + (demand.getCurrentStage() == null ? "." : " (" + demand.getCurrentStage().getName() + ").")));
    }

    private Optional<WorkflowTransition> findOptional(Demand demand, String action) {
        if (demand.getCurrentStage() == null) {
            return Optional.empty();
        }
        return transitions.findFromStage(demand.getCurrentStage().getId()).stream()
                .filter(t -> t.getAction().equals(action)).findFirst();
    }

    public record AvailableTransition(String action, String label, String toStageCode, String toStageName,
                                      boolean requiresReason, boolean forward, List<String> blockedBy) {}
}
