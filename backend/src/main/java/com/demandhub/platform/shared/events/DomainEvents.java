package com.demandhub.platform.shared.events;

import com.demandhub.platform.workflow.domain.StageAction;
import com.demandhub.platform.workflow.domain.StageCategory;
import java.util.Set;
import java.util.UUID;

/** Eventos de domínio publicados in-process (Spring ApplicationEvents). */
public final class DomainEvents {

    private DomainEvents() {}

    public record DemandSubmitted(UUID demandId) {}

    public record DemandStageChanged(UUID demandId, String fromStage, String toStage, String toStageName,
                                     StageCategory toCategory, String action, String actorName, String reason,
                                     Set<StageAction> onEnterActions) {}

    /** Campos relevantes alterados após o envio (ex.: prioridade/projeto) — usado para sincronizar o Jira. */
    public record DemandUpdated(UUID demandId, Set<String> changedFields) {}

    public record InformationRequested(UUID demandId, UUID requestId) {}

    public record InformationProvided(UUID demandId, UUID requestId) {}

    public record ApprovalRequested(UUID demandId, UUID approvalId, String approverRole, String ruleName) {}

    public record ApprovalDecided(UUID demandId, UUID approvalId, String stageCode, boolean approved, String comment) {}

    public record ExecutionStatusChanged(UUID demandId, UUID executionId, String fromStatus, String toStatus, boolean done) {}

    public record TriageAnalysisCompleted(UUID demandId, boolean success) {}
}
