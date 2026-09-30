package com.demandhub.platform.refinement.domain;

/** Namespace dos tipos do módulo de refinamento (entidades estão em arquivos próprios). */
public final class RefinementEntities {

    private RefinementEntities() {}

    public enum ItemType {
        FUNCTIONAL_REQUIREMENT, NON_FUNCTIONAL_REQUIREMENT, ACCEPTANCE_CRITERION, DEPENDENCY, RISK, QUESTION, PENDING_ITEM
    }

    public enum ItemStatus { OPEN, RESOLVED }

    public enum ItemOrigin { HUMAN, AI_ACCEPTED }

    public enum DecisionType { BUSINESS, ARCHITECTURAL, TECHNICAL }
}
