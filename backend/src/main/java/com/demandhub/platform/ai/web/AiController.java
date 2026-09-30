package com.demandhub.platform.ai.web;

import com.demandhub.platform.ai.agent.Agent;
import com.demandhub.platform.ai.agent.AgentOrchestrator;
import com.demandhub.platform.ai.agent.technical.DevelopmentSquadRegistry;
import com.demandhub.platform.ai.domain.AiAgentRun;
import com.demandhub.platform.ai.domain.AiAnalysis;
import com.demandhub.platform.ai.domain.AiSuggestion;
import com.demandhub.platform.ai.domain.ChatMessage;
import com.demandhub.platform.ai.repository.AiRepositories.AiAgentRunRepository;
import com.demandhub.platform.ai.service.AiSuggestionService;
import com.demandhub.platform.ai.service.AiSuggestionService.DecisionAction;
import com.demandhub.platform.ai.service.ConsistencyService;
import com.demandhub.platform.ai.service.IntakeChatService;
import com.demandhub.platform.ai.service.TechnicalArtifactService;
import com.demandhub.platform.ai.service.TriageAnalysisService;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.service.DemandService;
import com.demandhub.platform.document.domain.Document;
import com.demandhub.platform.shared.error.ApiException;
import com.demandhub.platform.shared.security.CurrentUser;
import com.demandhub.platform.shared.security.Permissions;
import com.demandhub.platform.shared.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Comparator;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiController {

    private final DemandService demandService;
    private final AiSuggestionService suggestions;
    private final IntakeChatService chat;
    private final TriageAnalysisService triage;
    private final TechnicalArtifactService artifacts;
    private final ConsistencyService consistency;
    private final AgentOrchestrator orchestrator;
    private final List<Agent<?, ?>> agents;
    private final DevelopmentSquadRegistry squad;
    private final AiAgentRunRepository runs;

    public AiController(DemandService demandService, AiSuggestionService suggestions, IntakeChatService chat,
                        TriageAnalysisService triage, TechnicalArtifactService artifacts, ConsistencyService consistency,
                        AgentOrchestrator orchestrator, List<Agent<?, ?>> agents, DevelopmentSquadRegistry squad, AiAgentRunRepository runs) {
        this.demandService = demandService;
        this.suggestions = suggestions;
        this.chat = chat;
        this.triage = triage;
        this.artifacts = artifacts;
        this.consistency = consistency;
        this.orchestrator = orchestrator;
        this.agents = agents;
        this.squad = squad;
        this.runs = runs;
    }

    // ------------------------------------------------------------- status e catálogo de agentes

    @GetMapping("/api/ai/status")
    public Map<String, Object> status() {
        return Map.of("provider", orchestrator.providerDescription(), "mock", orchestrator.isMockMode());
    }

    @GetMapping("/api/ai/agents")
    public Map<String, Object> agents() {
        List<Map<String, String>> list = agents.stream().sorted(Comparator.comparing(Agent::name))
                .map(a -> Map.of("name", a.name(), "responsibility", a.responsibility())).toList();
        List<Map<String, String>> deterministic = List.of(
                Map.of("name", ConsistencyService.AGENT, "responsibility", "Comparar formulário, documentação e apresentação e apontar divergências (estratégia determinística)."),
                Map.of("name", "ProgressSummaryAgent", "responsibility", "Resumir o avanço da execução técnica no Jira usando templates configurados por status (determinístico)."),
                Map.of("name", "DevelopmentOrchestrator", "responsibility", "Gerar o plano do squad de agentes a partir do registro de papéis (execução por runner externo)."));
        return Map.of("agents", list, "deterministicAgents", deterministic, "developmentSquad", squad.agents());
    }

    // ------------------------------------------------------------- triagem

    @GetMapping("/api/demands/{id}/ai-analysis")
    @Transactional(readOnly = true)
    public AnalysisView triageAnalysis(@PathVariable UUID id) {
        demandService.getForView(id, SecurityUtils.currentUser());
        return artifacts.latest(id, AiAnalysis.Kind.TRIAGE).map(AnalysisView::from).orElse(null);
    }

    @PostMapping("/api/demands/{id}/ai-analysis/run")
    @PreAuthorize("hasAuthority('DEMAND_TRIAGE')")
    public AnalysisView rerunTriage(@PathVariable UUID id) {
        Demand d = demandService.getForView(id, SecurityUtils.currentUser());
        if (d.isDraft()) {
            throw ApiException.businessRule("DEMAND_IS_DRAFT", "A triagem só é executada após o envio.");
        }
        return AnalysisView.from(triage.run(id));
    }

    // ------------------------------------------------------------- artefatos (arquitetura, spec, prompt, squad, docs)

    @GetMapping("/api/demands/{id}/artifacts/{kind}")
    @Transactional(readOnly = true)
    public AnalysisView artifact(@PathVariable UUID id, @PathVariable AiAnalysis.Kind kind) {
        demandService.getForView(id, SecurityUtils.currentUser());
        return artifacts.latest(id, kind).map(AnalysisView::from).orElse(null);
    }

    @PostMapping("/api/demands/{id}/artifacts/{kind}")
    public AnalysisView generate(@PathVariable UUID id, @PathVariable AiAnalysis.Kind kind) {
        CurrentUser user = SecurityUtils.currentUser();
        AiAnalysis a = switch (kind) {
            case ARCHITECTURE -> artifacts.generateArchitecture(id, user);
            case TECH_SPEC -> artifacts.generateSpecification(id, user);
            case TECH_PROMPT -> artifacts.generateTechnicalPrompt(id, user);
            case SQUAD_PLAN -> artifacts.generateSquadPlan(id, user);
            case TECHNICAL_DOC -> artifacts.generateDocumentation(id, Document.Kind.TECHNICAL_DOC, user);
            case CLIENT_DOC -> artifacts.generateDocumentation(id, Document.Kind.CLIENT_DOC, user);
            default -> throw ApiException.badRequest("INVALID_KIND", "Use o endpoint de triagem para " + kind);
        };
        return AnalysisView.from(a);
    }

    @PutMapping("/api/demands/{id}/analyses/{analysisId}")
    public AnalysisView edit(@PathVariable UUID id, @PathVariable UUID analysisId, @Valid @RequestBody EditAnalysisRequest req) {
        return AnalysisView.from(artifacts.edit(id, analysisId, req.content(), req.reason(), SecurityUtils.currentUser()));
    }

    // ------------------------------------------------------------- sugestões

    @GetMapping("/api/demands/{id}/suggestions")
    public List<AiSuggestion> suggestions(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean pending) {
        return suggestions.list(id, pending, SecurityUtils.currentUser());
    }

    @PostMapping("/api/demands/{id}/suggestions/{suggestionId}/decision")
    @Transactional
    public AiSuggestion decide(@PathVariable UUID id, @PathVariable UUID suggestionId, @Valid @RequestBody SuggestionDecision req) {
        return suggestions.decide(id, suggestionId, req.action(), req.value(), req.reason(), SecurityUtils.currentUser());
    }

    @PostMapping("/api/demands/{id}/consistency")
    @Transactional
    public Map<String, Object> checkConsistency(@PathVariable UUID id) {
        Demand d = demandService.getForView(id, SecurityUtils.currentUser());
        return Map.of("divergences", consistency.check(d));
    }

    // ------------------------------------------------------------- chatbot de abertura

    @GetMapping("/api/demands/{id}/chat")
    public List<ChatMessage> chatHistory(@PathVariable UUID id) {
        return chat.history(id, SecurityUtils.currentUser());
    }

    @PostMapping("/api/demands/{id}/chat")
    @PreAuthorize("hasAuthority('AI_USE')")
    public IntakeChatService.ChatReply chat(@PathVariable UUID id, @Valid @RequestBody ChatRequest req) {
        return chat.send(id, req.message(), SecurityUtils.currentUser());
    }

    // ------------------------------------------------------------- rastreabilidade

    @GetMapping("/api/demands/{id}/agent-runs")
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    public List<AiAgentRun> agentRuns(@PathVariable UUID id) {
        CurrentUser user = SecurityUtils.currentUser();
        demandService.getForView(id, user);
        if (!user.has(Permissions.AUDIT_VIEW)) {
            throw ApiException.forbidden("Acesso restrito.");
        }
        return runs.findByDemandIdOrderByCreatedAtDesc(id);
    }

    public record ChatRequest(@NotBlank @Size(max = 4000) String message) {}

    public record SuggestionDecision(@NotNull DecisionAction action, @Size(max = 8000) String value, @Size(max = 2000) String reason) {}

    public record EditAnalysisRequest(@NotBlank @Size(max = 100000) String content, @Size(max = 2000) String reason) {}

    public record AnalysisView(UUID id, UUID demandId, AiAnalysis.Kind kind, String mode, String content, boolean edited,
                               UUID editedBy, java.time.Instant editedAt, java.time.Instant createdAt) {
        static AnalysisView from(AiAnalysis a) {
            return new AnalysisView(a.getId(), a.getDemandId(), a.getKind(), a.getMode(), a.effectiveContent(),
                    a.getEditedContent() != null, a.getEditedBy(), a.getEditedAt(), a.getCreatedAt());
        }
    }
}
