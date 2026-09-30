package com.demandhub.platform.orchestration;

import com.demandhub.platform.ai.service.TriageAnalysisService;
import com.demandhub.platform.audit.service.AuditService;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.repository.DemandRepositories.DemandRepository;
import com.demandhub.platform.execution.service.ExecutionService;
import com.demandhub.platform.integration.jira.JiraSyncService;
import com.demandhub.platform.shared.events.DomainEvents.DemandStageChanged;
import com.demandhub.platform.shared.events.DomainEvents.DemandUpdated;
import com.demandhub.platform.shared.events.DomainEvents.TriageAnalysisCompleted;
import com.demandhub.platform.shared.util.SystemTasks;
import com.demandhub.platform.workflow.domain.StageAction;
import com.demandhub.platform.workflow.service.WorkflowEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Executa (após o commit, de forma assíncrona) as ações configuradas nos estágios e mantém o Jira sincronizado.
 * Cada ação roda em transação própria como SYSTEM; falhas são registradas e não desfazem a transição.
 */
@Component
public class StageActionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(StageActionDispatcher.class);

    private final SystemTasks system;
    private final TriageAnalysisService triage;
    private final JiraSyncService jira;
    private final ExecutionService executions;
    private final DemandRepository demands;
    private final WorkflowEngine engine;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public StageActionDispatcher(SystemTasks system, TriageAnalysisService triage, JiraSyncService jira, ExecutionService executions,
                                 DemandRepository demands, WorkflowEngine engine, AuditService audit, ApplicationEventPublisher events) {
        this.system = system;
        this.triage = triage;
        this.jira = jira;
        this.executions = executions;
        this.demands = demands;
        this.engine = engine;
        this.audit = audit;
        this.events = events;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStageChanged(DemandStageChanged event) {
        for (StageAction action : event.onEnterActions()) {
            safely(event, action.name(), () -> run(action, event));
        }
        if (event.fromStage() != null) {
            safely(event, "JIRA_SYNC_STAGE", () -> system.run(() -> jira.syncStage(event.demandId())));
        }
    }

    private void run(StageAction action, DemandStageChanged event) {
        switch (action) {
            case RUN_TRIAGE_ANALYSIS -> runTriage(event);
            case CREATE_JIRA_ISSUE -> system.run(() -> jira.createIssue(event.demandId()));
            case CREATE_SCM_ISSUE -> system.run(() -> executions.createForDemand(event.demandId()));
        }
    }

    /** A falha da IA nunca bloqueia o processo: a demanda segue para a triagem do PMO sem a análise. */
    private void runTriage(DemandStageChanged event) {
        boolean success = true;
        try {
            system.run(() -> triage.run(event.demandId()));
        } catch (RuntimeException e) {
            success = false;
            log.warn("Triagem IA falhou para {}: {}", event.demandId(), e.getMessage());
            system.run(() -> audit.event("AI_TRIAGE_FAILED").actor("AI:TriageOrchestrator").entity("Demand", event.demandId())
                    .demand(event.demandId()).metadata(e.getClass().getSimpleName()).record());
        }
        system.run(() -> {
            Demand d = demands.findById(event.demandId()).orElseThrow();
            if (d.getCurrentStage() != null && d.getCurrentStage().getCode().equals(event.toStage())) {
                engine.systemTransition(d, "ANALYSIS_DONE", null);
            }
        });
        boolean ok = success;
        system.run(() -> events.publishEvent(new TriageAnalysisCompleted(event.demandId(), ok)));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDemandUpdated(DemandUpdated event) {
        if (event.changedFields().stream().anyMatch(f -> f.equals("priority") || f.equals("project") || f.equals("demandType"))) {
            try {
                system.run(() -> jira.syncFields(event.demandId()));
            } catch (RuntimeException e) {
                log.warn("Falha ao sincronizar campos no Jira: {}", e.getMessage());
            }
        }
    }

    private void safely(DemandStageChanged event, String name, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            log.error("Falha na ação {} da demanda {}", name, event.demandId(), e);
        }
    }
}
