package com.demandhub.platform.demand.domain;

/** Estado macro derivado da categoria do estágio atual. */
public enum LifecycleState {
    DRAFT, ACTIVE, ON_HOLD, REJECTED, COMPLETED, CANCELLED, LEGACY
}
