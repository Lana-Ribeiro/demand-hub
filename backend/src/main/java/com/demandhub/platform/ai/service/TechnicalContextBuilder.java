package com.demandhub.platform.ai.service;

import com.demandhub.platform.ai.agent.technical.TechnicalContext;
import com.demandhub.platform.ai.domain.AiAnalysis;
import com.demandhub.platform.ai.repository.AiRepositories.AiAnalysisRepository;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.execution.repository.ExecutionRepositories.ExecutionEventRepository;
import com.demandhub.platform.execution.repository.ExecutionRepositories.TechnicalExecutionRepository;
import com.demandhub.platform.integration.common.ExternalLink;
import com.demandhub.platform.integration.common.IntegrationRepositories.ExternalLinkRepository;
import com.demandhub.platform.refinement.repository.RefinementRepositories.DecisionRepository;
import com.demandhub.platform.refinement.repository.RefinementRepositories.RefinementItemRepository;
import com.demandhub.platform.shared.util.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Monta o contexto mínimo dos agentes técnicos a partir dos registros da demanda. */
@Component
public class TechnicalContextBuilder {

    private final RefinementItemRepository items;
    private final DecisionRepository decisions;
    private final AiAnalysisRepository analyses;
    private final ExternalLinkRepository links;
    private final TechnicalExecutionRepository executions;
    private final ExecutionEventRepository executionEvents;
    private final JsonSupport json;

    public TechnicalContextBuilder(RefinementItemRepository items, DecisionRepository decisions, AiAnalysisRepository analyses,
                                   ExternalLinkRepository links, TechnicalExecutionRepository executions,
                                   ExecutionEventRepository executionEvents, JsonSupport json) {
        this.items = items;
        this.decisions = decisions;
        this.analyses = analyses;
        this.links = links;
        this.executions = executions;
        this.executionEvents = executionEvents;
        this.json = json;
    }

    public TechnicalContext build(Demand d) {
        String architecture = analyses.findFirstByDemandIdAndKindOrderByCreatedAtDesc(d.getId(), AiAnalysis.Kind.ARCHITECTURE)
                .map(a -> {
                    JsonNode node = json.read(a.effectiveContent());
                    return node.path("proposal").asText(a.effectiveContent());
                }).orElse(null);
        String jiraKey = links.findByDemandIdAndSystem(d.getId(), ExternalLink.System.JIRA).map(ExternalLink::getExternalKey).orElse(null);
        String execution = executions.findByDemandId(d.getId())
                .map(e -> "Status atual: " + e.getStatusCode() + "\n" + executionEvents.findByExecutionIdOrderByReceivedAtAsc(e.getId()).stream()
                        .map(ev -> "- " + ev.getReceivedAt() + ": " + ev.getFromStatus() + " → " + ev.getToStatus())
                        .collect(Collectors.joining("\n")))
                .orElse(null);
        return new TechnicalContext(d.displayId(), d.getTitle(),
                d.getDemandType() == null ? null : d.getDemandType().getName(),
                d.getProject() == null ? null : d.getProject().getCode() + " — " + d.getProject().getName(),
                d.getObjective(), d.getCurrentProblem(), d.getExpectedBenefits(), d.getScopeDescription(), d.getOutOfScope(),
                d.getSystemsInvolved(),
                items.findByDemandIdOrderByCreatedAtAsc(d.getId()).stream()
                        .map(i -> new TechnicalContext.Item(i.getType().name(), i.getDescription())).toList(),
                decisions.findByDemandIdOrderByCreatedAtAsc(d.getId()).stream()
                        .map(x -> new TechnicalContext.Item(x.getType().name(), x.getDescription())).toList(),
                architecture, jiraKey, execution);
    }
}
