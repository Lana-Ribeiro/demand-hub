package com.demandhub.platform.workflow.domain;

import com.demandhub.platform.demand.domain.LifecycleState;

/** Categoria sistêmica de um estágio configurável. Usada para comportamento, agrupamento e métricas. */
public enum StageCategory {
    DRAFT, INTAKE, TRIAGE, ON_HOLD, ANALYSIS, APPROVAL, REFINEMENT, ARCHITECTURE,
    READY, EXECUTION, DOCUMENTATION, DONE, REJECTED, CANCELLED;

    public LifecycleState lifecycleState() {
        return switch (this) {
            case DRAFT -> LifecycleState.DRAFT;
            case ON_HOLD -> LifecycleState.ON_HOLD;
            case DONE -> LifecycleState.COMPLETED;
            case REJECTED -> LifecycleState.REJECTED;
            case CANCELLED -> LifecycleState.CANCELLED;
            default -> LifecycleState.ACTIVE;
        };
    }

    public boolean isTerminal() {
        return this == DONE || this == REJECTED || this == CANCELLED;
    }
}
