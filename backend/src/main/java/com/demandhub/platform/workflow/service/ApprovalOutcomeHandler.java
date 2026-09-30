package com.demandhub.platform.workflow.service;

import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.repository.DemandRepositories.DemandRepository;
import com.demandhub.platform.shared.events.DomainEvents.ApprovalDecided;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reage (na mesma transação) a decisões de aprovação:
 * aprovada → tenta auto-avanço; rejeitada → transição sistêmica REJECT, se configurada no estágio.
 */
@Component
public class ApprovalOutcomeHandler {

    private final DemandRepository demands;
    private final WorkflowEngine engine;

    public ApprovalOutcomeHandler(DemandRepository demands, WorkflowEngine engine) {
        this.demands = demands;
        this.engine = engine;
    }

    @EventListener
    public void on(ApprovalDecided event) {
        Demand demand = demands.findById(event.demandId()).orElseThrow();
        if (event.approved()) {
            engine.tryAutoAdvance(demand);
        } else {
            engine.systemTransition(demand, "REJECT", "Aprovação rejeitada: " + event.comment());
        }
    }
}
