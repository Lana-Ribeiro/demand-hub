package com.demandhub.platform.ai.service;

import com.demandhub.platform.ai.agent.AgentOrchestrator;
import com.demandhub.platform.ai.agent.AgentResult;
import com.demandhub.platform.ai.agent.technical.DevelopmentSquadRegistry;
import com.demandhub.platform.ai.agent.technical.TechnicalAgents;
import com.demandhub.platform.ai.agent.technical.TechnicalAgents.DocumentationInput;
import com.demandhub.platform.ai.agent.technical.TechnicalAgents.MarkdownOutput;
import com.demandhub.platform.ai.agent.technical.TechnicalContext;
import com.demandhub.platform.ai.domain.AiAnalysis;
import com.demandhub.platform.ai.domain.AiSuggestion;
import com.demandhub.platform.ai.repository.AiRepositories.AiAnalysisRepository;
import com.demandhub.platform.audit.service.AuditService;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.service.DemandService;
import com.demandhub.platform.document.domain.Document;
import com.demandhub.platform.document.service.DocumentService;
import com.demandhub.platform.refinement.domain.Meeting;
import com.demandhub.platform.refinement.repository.RefinementRepositories.MeetingRepository;
import com.demandhub.platform.shared.error.ApiException;
import com.demandhub.platform.shared.security.CurrentUser;
import com.demandhub.platform.shared.security.Permissions;
import com.demandhub.platform.shared.util.JsonSupport;
import com.demandhub.platform.shared.util.Texts;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Geração assistida de artefatos: refinamento a partir de reunião, arquitetura, especificação técnica, prompt técnico
 * (Estratégia A), plano do squad (Estratégia B) e documentação. Tudo é revisável/editável por humanos.
 */
@Service
public class TechnicalArtifactService {

    private final DemandService demandService;
    private final MeetingRepository meetings;
    private final AiAnalysisRepository analyses;
    private final TechnicalContextBuilder contextBuilder;
    private final AgentOrchestrator orchestrator;
    private final TechnicalAgents.RefinementAgent refinementAgent;
    private final TechnicalAgents.ArchitectureAgent architectureAgent;
    private final TechnicalAgents.TechnicalSpecificationAgent specAgent;
    private final TechnicalAgents.PromptEngineerAgent promptAgent;
    private final TechnicalAgents.TechnicalDocumentationAgent techDocAgent;
    private final TechnicalAgents.ClientDocumentationAgent clientDocAgent;
    private final DevelopmentSquadRegistry squad;
    private final AiSuggestionService suggestions;
    private final DocumentService documentService;
    private final AuditService audit;
    private final JsonSupport json;
    private final Clock clock;

    public TechnicalArtifactService(DemandService demandService, MeetingRepository meetings, AiAnalysisRepository analyses,
                                    TechnicalContextBuilder contextBuilder, AgentOrchestrator orchestrator,
                                    TechnicalAgents.RefinementAgent refinementAgent, TechnicalAgents.ArchitectureAgent architectureAgent,
                                    TechnicalAgents.TechnicalSpecificationAgent specAgent, TechnicalAgents.PromptEngineerAgent promptAgent,
                                    TechnicalAgents.TechnicalDocumentationAgent techDocAgent,
                                    TechnicalAgents.ClientDocumentationAgent clientDocAgent, DevelopmentSquadRegistry squad,
                                    AiSuggestionService suggestions, DocumentService documentService, AuditService audit,
                                    JsonSupport json, Clock clock) {
        this.demandService = demandService;
        this.meetings = meetings;
        this.analyses = analyses;
        this.contextBuilder = contextBuilder;
        this.orchestrator = orchestrator;
        this.refinementAgent = refinementAgent;
        this.architectureAgent = architectureAgent;
        this.specAgent = specAgent;
        this.promptAgent = promptAgent;
        this.techDocAgent = techDocAgent;
        this.clientDocAgent = clientDocAgent;
        this.squad = squad;
        this.suggestions = suggestions;
        this.documentService = documentService;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    // ------------------------------------------------------------- Refinement Agent

    @Transactional
    public List<AiSuggestion> suggestFromMeeting(UUID demandId, UUID meetingId, CurrentUser user) {
        require(user, Permissions.REFINEMENT_MANAGE);
        Demand d = demandService.getForView(demandId, user);
        Meeting m = meetings.findById(meetingId).filter(x -> x.getDemandId().equals(demandId))
                .orElseThrow(() -> ApiException.notFound("Reunião", meetingId));
        if (Texts.isBlank(m.getNotes())) {
            throw ApiException.businessRule("MEETING_WITHOUT_NOTES", "A reunião não possui notas para analisar.");
        }
        TechnicalContext ctx = contextBuilder.build(d);
        AgentResult<TechnicalAgents.RefinementAgent.Output> r = orchestrator.run(refinementAgent,
                new TechnicalAgents.RefinementAgent.Input(d.getTitle(), d.getObjective(), m.getNotes(), ctx.requirements()), demandId);
        if (!r.success()) {
            throw ApiException.businessRule("AI_FAILED", r.error());
        }
        List<AiSuggestion> created = new java.util.ArrayList<>();
        r.output().items().forEach(p -> created.add(suggestions.create(demandId, r.runId(), refinementAgent.name(),
                AiSuggestion.Kind.REFINEMENT_ITEM, p.type(), p.description(), null, 0.6, AiSuggestion.SourceType.MEETING,
                meetingId.toString(), "Extraído das notas da reunião \"" + m.getTitle() + "\".")));
        r.output().decisions().forEach(p -> created.add(suggestions.create(demandId, r.runId(), refinementAgent.name(),
                AiSuggestion.Kind.REFINEMENT_ITEM, "DECISION:" + p.type(), p.description(), null, 0.6, AiSuggestion.SourceType.MEETING,
                meetingId.toString(), "Decisão identificada nas notas da reunião \"" + m.getTitle() + "\".")));
        return created;
    }

    // ------------------------------------------------------------- Arquitetura / especificação / prompt / squad

    @Transactional
    public AiAnalysis generateArchitecture(UUID demandId, CurrentUser user) {
        requireAny(user, Permissions.ARCHITECTURE_MANAGE, Permissions.REFINEMENT_MANAGE);
        Demand d = editable(demandId, user);
        AgentResult<TechnicalAgents.ArchitectureAgent.Output> r = orchestrator.run(architectureAgent, contextBuilder.build(d), demandId);
        return save(d, AiAnalysis.Kind.ARCHITECTURE, r, r.success() ? json.write(r.output()) : null);
    }

    @Transactional
    public AiAnalysis generateSpecification(UUID demandId, CurrentUser user) {
        requireAny(user, Permissions.ARCHITECTURE_MANAGE, Permissions.REFINEMENT_MANAGE, Permissions.EXECUTION_MANAGE);
        Demand d = editable(demandId, user);
        AgentResult<MarkdownOutput> r = orchestrator.run(specAgent, contextBuilder.build(d), demandId);
        return save(d, AiAnalysis.Kind.TECH_SPEC, r, r.success() ? json.write(Map.of("markdown", r.output().markdown())) : null);
    }

    /** Estratégia A: prompt técnico estruturado (estrutura determinística + orientações do agente). */
    @Transactional
    public AiAnalysis generateTechnicalPrompt(UUID demandId, CurrentUser user) {
        requireAny(user, Permissions.ARCHITECTURE_MANAGE, Permissions.REFINEMENT_MANAGE, Permissions.EXECUTION_MANAGE);
        Demand d = editable(demandId, user);
        TechnicalContext ctx = contextBuilder.build(d);
        AgentResult<TechnicalAgents.PromptEngineerAgent.Output> r = orchestrator.run(promptAgent, ctx, demandId);
        String spec = latest(demandId, AiAnalysis.Kind.TECH_SPEC).map(a -> json.read(a.effectiveContent()).path("markdown").asText(null)).orElse(null);
        String markdown = TechnicalPromptTemplate.render(ctx, spec, r.success() ? r.output() : null);
        return save(d, AiAnalysis.Kind.TECH_PROMPT, r, json.write(Map.of("markdown", markdown)));
    }

    /** Estratégia B: plano/manifesto para o squad de agentes (execução por runner externo, não habilitado). */
    @Transactional
    public AiAnalysis generateSquadPlan(UUID demandId, CurrentUser user) {
        requireAny(user, Permissions.ARCHITECTURE_MANAGE, Permissions.EXECUTION_MANAGE);
        Demand d = editable(demandId, user);
        TechnicalContext ctx = contextBuilder.build(d);
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("demand", ctx.protocol() + " — " + ctx.title());
        plan.put("runnerEnabled", false);
        plan.put("runnerNote", "Execução autônoma não habilitada no MVP: o plano é anexado à issue do repositório (GitLab/GitHub) para execução por runner externo aprovado.");
        plan.put("acceptanceCriteria", ctx.ofType("ACCEPTANCE_CRITERION").stream().map(TechnicalContext.Item::description).toList());
        plan.put("agents", squad.agents());
        plan.put("sequence", squad.agents().stream().map(DevelopmentSquadRegistry.SquadAgent::name).collect(Collectors.joining(" → ")));
        AiAnalysis a = new AiAnalysis();
        a.setDemandId(demandId);
        a.setKind(AiAnalysis.Kind.SQUAD_PLAN);
        a.setMode("REAL");
        a.setContent(json.write(plan));
        a.setCreatedAt(clock.instant());
        analyses.save(a);
        audit.event("AI_ARTIFACT_GENERATED").entity("AiAnalysis", a.getId()).demand(demandId).metadata("kind=SQUAD_PLAN").record();
        return a;
    }

    // ------------------------------------------------------------- Documentação

    @Transactional
    public AiAnalysis generateDocumentation(UUID demandId, Document.Kind kind, CurrentUser user) {
        requireAny(user, Permissions.DEMAND_TRANSITION, Permissions.EXECUTION_MANAGE);
        if (kind != Document.Kind.TECHNICAL_DOC && kind != Document.Kind.CLIENT_DOC) {
            throw ApiException.badRequest("INVALID_KIND", "Tipo de documentação inválido.");
        }
        Demand d = editable(demandId, user);
        TechnicalContext ctx = contextBuilder.build(d);
        boolean technical = kind == Document.Kind.TECHNICAL_DOC;
        AgentResult<MarkdownOutput> r = technical
                ? orchestrator.run(techDocAgent, new DocumentationInput(ctx, "TECHNICAL"), demandId)
                : orchestrator.run(clientDocAgent, new DocumentationInput(ctx, "CLIENT"), demandId);
        AiAnalysis a = save(d, technical ? AiAnalysis.Kind.TECHNICAL_DOC : AiAnalysis.Kind.CLIENT_DOC, r,
                r.success() ? json.write(Map.of("markdown", r.output().markdown())) : null);
        documentService.saveGenerated(demandId, kind, fileName(d, technical), r.output().markdown(), user.id());
        return a;
    }

    // ------------------------------------------------------------- Consulta e edição humana

    @Transactional(readOnly = true)
    public Optional<AiAnalysis> latest(UUID demandId, AiAnalysis.Kind kind) {
        return analyses.findFirstByDemandIdAndKindOrderByCreatedAtDesc(demandId, kind);
    }

    @Transactional
    public AiAnalysis edit(UUID demandId, UUID analysisId, String content, String reason, CurrentUser user) {
        AiAnalysis a = analyses.findById(analysisId).filter(x -> x.getDemandId().equals(demandId))
                .orElseThrow(() -> ApiException.notFound("Análise", analysisId));
        String permission = switch (a.getKind()) {
            case TRIAGE -> Permissions.DEMAND_TRIAGE;
            case ARCHITECTURE -> Permissions.ARCHITECTURE_MANAGE;
            case TECHNICAL_DOC, CLIENT_DOC -> Permissions.DEMAND_TRANSITION;
            default -> Permissions.REFINEMENT_MANAGE;
        };
        if (!user.has(permission) && !(a.getKind() != AiAnalysis.Kind.TRIAGE && user.has(Permissions.EXECUTION_MANAGE))) {
            throw ApiException.forbidden("Sem permissão para editar esta análise.");
        }
        Demand d = editable(demandId, user);
        json.read(content); // valida JSON
        String before = a.effectiveContent();
        a.setEditedContent(content);
        a.setEditedBy(user.id());
        a.setEditedAt(clock.instant());
        audit.event("AI_ANALYSIS_EDITED").entity("AiAnalysis", analysisId).demand(demandId)
                .change(a.getKind().name(), Texts.truncate(before, 1500), Texts.truncate(content, 1500)).reason(reason).record();
        if (a.getKind() == AiAnalysis.Kind.TECHNICAL_DOC || a.getKind() == AiAnalysis.Kind.CLIENT_DOC) {
            boolean technical = a.getKind() == AiAnalysis.Kind.TECHNICAL_DOC;
            documentService.saveGenerated(demandId, technical ? Document.Kind.TECHNICAL_DOC : Document.Kind.CLIENT_DOC,
                    fileName(d, technical), json.read(content).path("markdown").asText(""), user.id());
        }
        return a;
    }

    private AiAnalysis save(Demand d, AiAnalysis.Kind kind, AgentResult<?> r, String content) {
        if (content == null) {
            throw ApiException.businessRule("AI_FAILED", r.error() == null ? "Falha ao gerar artefato." : r.error());
        }
        AiAnalysis a = new AiAnalysis();
        a.setDemandId(d.getId());
        a.setAgentRunId(r.runId());
        a.setKind(kind);
        a.setMode(r.mode());
        a.setContent(content);
        a.setCreatedAt(clock.instant());
        analyses.save(a);
        audit.event("AI_ARTIFACT_GENERATED").entity("AiAnalysis", a.getId()).demand(d.getId())
                .metadata("kind=" + kind + "; mode=" + r.mode()).record();
        return a;
    }

    private Demand editable(UUID demandId, CurrentUser user) {
        Demand d = demandService.getForView(demandId, user);
        if (d.isDraft() || d.isReadOnly()) {
            throw ApiException.businessRule("DEMAND_NOT_EDITABLE", "Ação indisponível no estado atual da demanda.");
        }
        return d;
    }

    private static String fileName(Demand d, boolean technical) {
        return (technical ? "documentacao-tecnica-" : "documentacao-cliente-") + d.displayId() + ".md";
    }

    private static void require(CurrentUser user, String permission) {
        if (!user.has(permission)) {
            throw ApiException.forbidden("Sem permissão para esta ação.");
        }
    }

    private static void requireAny(CurrentUser user, String... permissions) {
        for (String p : permissions) {
            if (user.has(p)) return;
        }
        throw ApiException.forbidden("Sem permissão para esta ação.");
    }
}
