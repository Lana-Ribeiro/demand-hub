package com.demandhub.platform.execution.web;

import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.repository.DemandRepositories.DemandRepository;
import com.demandhub.platform.demand.service.DemandQueryService;
import com.demandhub.platform.demand.service.DemandService;
import com.demandhub.platform.demand.web.DemandDtos.DemandSummary;
import com.demandhub.platform.execution.domain.ExecutionEvent;
import com.demandhub.platform.execution.domain.ExecutionStatus;
import com.demandhub.platform.execution.domain.TechnicalExecution;
import com.demandhub.platform.execution.repository.ExecutionRepositories.TechnicalExecutionRepository;
import com.demandhub.platform.execution.service.ExecutionService;
import com.demandhub.platform.integration.common.ExternalLink;
import com.demandhub.platform.integration.common.IntegrationConfig.IntegrationGateways;
import com.demandhub.platform.integration.common.IntegrationRepositories.ExternalLinkRepository;
import com.demandhub.platform.integration.common.IntegrationRepositories.WebhookEventRepository;
import com.demandhub.platform.integration.common.WebhookEvent;
import com.demandhub.platform.integration.jira.JiraSyncService;
import com.demandhub.platform.shared.security.CurrentUser;
import com.demandhub.platform.shared.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Execução técnica e integrações. O painel sempre separa "Ciclo da Demanda" de "Execução Técnica". */
@RestController
public class ExecutionController {

    private final ExecutionService service;
    private final TechnicalExecutionRepository executions;
    private final DemandService demandService;
    private final DemandRepository demands;
    private final DemandQueryService query;
    private final ExternalLinkRepository links;
    private final JiraSyncService jira;
    private final IntegrationGateways gateways;
    private final WebhookEventRepository webhookEvents;

    public ExecutionController(ExecutionService service, TechnicalExecutionRepository executions, DemandService demandService,
                               DemandRepository demands, DemandQueryService query, ExternalLinkRepository links, JiraSyncService jira,
                               IntegrationGateways gateways, WebhookEventRepository webhookEvents) {
        this.service = service;
        this.executions = executions;
        this.demandService = demandService;
        this.demands = demands;
        this.query = query;
        this.links = links;
        this.jira = jira;
        this.gateways = gateways;
        this.webhookEvents = webhookEvents;
    }

    @GetMapping("/api/demands/{id}/execution")
    @Transactional(readOnly = true)
    public ExecutionPanel panel(@PathVariable UUID id) {
        CurrentUser user = SecurityUtils.currentUser();
        Demand d = demandService.getForView(id, user);
        TechnicalExecution exec = service.forDemand(id, user).orElse(null);
        ExternalLink repository = exec == null || exec.getExternalLinkId() == null ? null : links.findById(exec.getExternalLinkId()).orElse(null);
        ExternalLink jiraLink = links.findByDemandIdAndSystem(id, ExternalLink.System.JIRA).orElse(null);
        return new ExecutionPanel(
                new DemandLifecycle(d.getCurrentStage() == null ? null : d.getCurrentStage().getName(),
                        d.getCurrentStage() == null ? null : d.getCurrentStage().getJiraStatus(), d.getLifecycleState().name(),
                        jiraLink == null ? null : view(jiraLink)),
                exec == null ? null : new TechnicalLifecycle(exec.getId(), exec.getStatusCode(), exec.getStrategy().name(), "GITHUB".equals(exec.getSystem()) ? "GitHub" : "GitLab",
                        exec.getStartedAt(), exec.getCompletedAt(), repository == null ? null : view(repository), service.eventsOf(exec.getId())),
                service.statuses(), gateways.jiraMode().name(), gateways.scmMode().name(), gateways.scmName(),
                d.getDemandType() != null && d.getDemandType().isTechnical());
    }

    @GetMapping("/api/executions")
    @PreAuthorize("hasAuthority('EXECUTION_VIEW')")
    @Transactional(readOnly = true)
    public List<ExecutionRow> list() {
        List<TechnicalExecution> all = executions.findAllByOrderByLastEventAtDesc();
        Map<UUID, DemandSummary> summaries = new java.util.HashMap<>();
        query.summaries(demands.findAllById(all.stream().map(TechnicalExecution::getDemandId).toList()))
                .forEach(s -> summaries.put(s.id(), s));
        return all.stream().map(e -> new ExecutionRow(e.getId(), e.getStatusCode(), e.getStrategy().name(), e.getLastEventAt(),
                summaries.get(e.getDemandId()))).toList();
    }

    @PutMapping("/api/demands/{id}/execution/strategy")
    @Transactional
    public Map<String, String> strategy(@PathVariable UUID id, @Valid @RequestBody StrategyRequest req) {
        TechnicalExecution e = service.setStrategy(id, req.strategy(), SecurityUtils.currentUser());
        return Map.of("strategy", e.getStrategy().name());
    }

    /** Registro manual (somente quando GITLAB_MODE=disabled). */
    @PostMapping("/api/demands/{id}/execution/status")
    @Transactional
    public Map<String, String> manualStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest req) {
        return Map.of("status", service.manualStatus(id, req.status(), req.note(), SecurityUtils.currentUser()).getStatusCode());
    }

    /** Simulação explícita de evento do repositório (somente em modo mock). */
    @PostMapping("/api/integrations/scm/mock/executions/{demandId}/status")
    @Transactional
    public Map<String, String> simulate(@PathVariable UUID demandId, @Valid @RequestBody StatusRequest req) {
        return Map.of("status", service.simulateScmStatus(demandId, req.status(), SecurityUtils.currentUser()).getStatusCode(),
                "mode", "MOCK");
    }

    @PostMapping("/api/demands/{id}/jira")
    @PreAuthorize("hasAnyAuthority('INTEGRATION_MANAGE','DEMAND_TRIAGE')")
    @Transactional
    public LinkView jiraSync(@PathVariable UUID id) {
        demandService.getForView(id, SecurityUtils.currentUser());
        return jira.retry(id).map(ExecutionController::view).orElse(null);
    }

    @PostMapping("/api/demands/{id}/scm/retry")
    @Transactional
    public Map<String, String> scmRetry(@PathVariable UUID id) {
        return Map.of("execution", service.retryScm(id, SecurityUtils.currentUser()).getId().toString());
    }

    @GetMapping("/api/integrations/status")
    public Map<String, String> integrationStatus() {
        return Map.of("jira", gateways.jiraMode().name(), "scm", gateways.scmMode().name(), "scmSystem", gateways.scmName());
    }

    @GetMapping("/api/integrations/webhook-events")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public List<WebhookEvent> webhookEvents() {
        return webhookEvents.findTop100ByOrderByReceivedAtDesc();
    }

    static LinkView view(ExternalLink l) {
        return new LinkView(l.getSystem().name(), l.getMode().name(), l.getExternalKey(), l.getProjectRef(), l.getUrl(),
                l.getSyncStatus().name(), l.getLastError(), l.getLastSyncAt());
    }

    public record LinkView(String system, String mode, String key, String projectRef, String url, String syncStatus,
                           String lastError, java.time.Instant lastSyncAt) {}

    public record DemandLifecycle(String stage, String jiraStatus, String lifecycleState, LinkView jira) {}

    public record TechnicalLifecycle(UUID id, String status, String strategy, String system, java.time.Instant startedAt,
                                     java.time.Instant completedAt, LinkView repository, List<ExecutionEvent> events) {}

    public record ExecutionPanel(DemandLifecycle demandLifecycle, TechnicalLifecycle technicalExecution,
                                 List<ExecutionStatus> statuses, String jiraMode, String scmMode, String scmSystem, boolean technicalDemand) {}

    public record ExecutionRow(UUID id, String status, String strategy, java.time.Instant lastEventAt, DemandSummary demand) {}

    public record StrategyRequest(@NotNull TechnicalExecution.Strategy strategy) {}

    public record StatusRequest(@NotBlank @Size(max = 40) String status, @Size(max = 2000) String note) {}
}
