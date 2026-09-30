package com.demandhub.platform.ai.service;

import com.demandhub.platform.ai.agent.AgentOrchestrator;
import com.demandhub.platform.ai.agent.AgentResult;
import com.demandhub.platform.ai.agent.intake.ClassificationAgent;
import com.demandhub.platform.ai.agent.intake.CompletenessAgent;
import com.demandhub.platform.ai.agent.intake.DuplicateDetectionAgent;
import com.demandhub.platform.ai.agent.intake.FeasibilityAgent;
import com.demandhub.platform.ai.agent.intake.ImpactAgent;
import com.demandhub.platform.ai.agent.intake.PriorityAgent;
import com.demandhub.platform.ai.agent.intake.SummaryAgent;
import com.demandhub.platform.ai.domain.AiAnalysis;
import com.demandhub.platform.ai.domain.AiSuggestion;
import com.demandhub.platform.ai.repository.AiRepositories.AiAnalysisRepository;
import com.demandhub.platform.ai.repository.AiRepositories.AiSuggestionRepository;
import com.demandhub.platform.audit.service.AuditService;
import com.demandhub.platform.catalog.repository.CatalogRepositories.DemandTypeRepository;
import com.demandhub.platform.catalog.repository.CatalogRepositories.PriorityRepository;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.domain.DemandField;
import com.demandhub.platform.demand.repository.DemandRepositories.DemandRepository;
import com.demandhub.platform.demand.service.DemandFieldAccessor;
import com.demandhub.platform.demand.service.DemandSubmissionValidator;
import com.demandhub.platform.document.domain.Document;
import com.demandhub.platform.document.repository.DocumentRepository;
import com.demandhub.platform.project.repository.ProjectRepository;
import com.demandhub.platform.shared.util.JsonSupport;
import com.demandhub.platform.shared.util.Texts;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triagem IA: executa cada agente com contexto mínimo, consolida a análise estruturada (editável pelo PMO)
 * e registra sugestões de tipo/projeto/prioridade, lacunas e duplicidades. A IA nunca aprova.
 * A recomendação de encaminhamento é determinística (derivada dos resultados), não do prompt.
 */
@Service
public class TriageAnalysisService {

    private final DemandRepository demands;
    private final DemandFieldAccessor accessor;
    private final DemandSubmissionValidator validator;
    private final DemandTypeRepository demandTypes;
    private final ProjectRepository projects;
    private final PriorityRepository priorities;
    private final DocumentRepository documents;
    private final AgentOrchestrator orchestrator;
    private final SummaryAgent summaryAgent;
    private final ClassificationAgent classificationAgent;
    private final PriorityAgent priorityAgent;
    private final ImpactAgent impactAgent;
    private final FeasibilityAgent feasibilityAgent;
    private final CompletenessAgent completenessAgent;
    private final DuplicateDetectionAgent duplicateAgent;
    private final DuplicateCandidateFinder candidateFinder;
    private final ConsistencyService consistency;
    private final AiSuggestionService suggestions;
    private final AiSuggestionRepository suggestionRepo;
    private final AiAnalysisRepository analyses;
    private final AuditService audit;
    private final JsonSupport json;
    private final Clock clock;

    public TriageAnalysisService(DemandRepository demands, DemandFieldAccessor accessor, DemandSubmissionValidator validator,
                                 DemandTypeRepository demandTypes, ProjectRepository projects, PriorityRepository priorities,
                                 DocumentRepository documents, AgentOrchestrator orchestrator, SummaryAgent summaryAgent,
                                 ClassificationAgent classificationAgent, PriorityAgent priorityAgent, ImpactAgent impactAgent,
                                 FeasibilityAgent feasibilityAgent, CompletenessAgent completenessAgent,
                                 DuplicateDetectionAgent duplicateAgent, DuplicateCandidateFinder candidateFinder,
                                 ConsistencyService consistency, AiSuggestionService suggestions, AiSuggestionRepository suggestionRepo,
                                 AiAnalysisRepository analyses, AuditService audit, JsonSupport json, Clock clock) {
        this.demands = demands;
        this.accessor = accessor;
        this.validator = validator;
        this.demandTypes = demandTypes;
        this.projects = projects;
        this.priorities = priorities;
        this.documents = documents;
        this.orchestrator = orchestrator;
        this.summaryAgent = summaryAgent;
        this.classificationAgent = classificationAgent;
        this.priorityAgent = priorityAgent;
        this.impactAgent = impactAgent;
        this.feasibilityAgent = feasibilityAgent;
        this.completenessAgent = completenessAgent;
        this.duplicateAgent = duplicateAgent;
        this.candidateFinder = candidateFinder;
        this.consistency = consistency;
        this.suggestions = suggestions;
        this.suggestionRepo = suggestionRepo;
        this.analyses = analyses;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public AiAnalysis run(UUID demandId) {
        Demand d = demands.findById(demandId).orElseThrow();
        Map<String, String> form = accessor.readAll(d);
        List<String> failed = new ArrayList<>();
        Map<String, Object> content = new LinkedHashMap<>();
        boolean anyMock = false;

        // Resumo executivo (formulário + destaques dos documentos)
        String highlights = documents.findByDemandIdOrderByUploadedAtAsc(demandId).stream()
                .filter(doc -> doc.getKind() == Document.Kind.DOCUMENT || doc.getKind() == Document.Kind.PRESENTATION)
                .filter(doc -> doc.getExtractedText() != null)
                .map(doc -> "[" + doc.getFileName() + "]\n" + Texts.truncate(doc.getExtractedText(), 3000))
                .collect(Collectors.joining("\n\n"));
        AgentResult<SummaryAgent.Output> summary = orchestrator.run(summaryAgent, new SummaryAgent.Input(form, highlights), demandId);
        anyMock |= summary.isMock();
        if (summary.success()) {
            content.put("summary", summary.output());
        } else {
            failed.add(summaryAgent.name());
        }

        // Classificação (tipo + projeto)
        ClassificationAgent.Input classIn = new ClassificationAgent.Input(d.getTitle(), d.getObjective(), d.getCurrentProblem(),
                form.get("demandType"),
                demandTypes.findByActiveTrueOrderByNameAsc().stream().map(t -> new ClassificationAgent.Option(t.getCode(), t.getName(), t.getDescription())).toList(),
                projects.findByActiveTrueOrderByNameAsc().stream().map(p -> new ClassificationAgent.Option(p.getCode(), p.getName(), p.getDescription())).toList());
        AgentResult<ClassificationAgent.Output> classification = orchestrator.run(classificationAgent, classIn, demandId);
        if (classification.success()) {
            ClassificationAgent.Output c = classificationAgent.validate(classification.output(), classIn);
            content.put("classification", c);
            suggestIfDifferent(d, DemandField.DEMAND_TYPE, c.typeCode(), c.typeConfidence(), c.typeRationale(), classification.runId(), classificationAgent.name());
            suggestIfDifferent(d, DemandField.PROJECT, c.projectCode(), c.projectConfidence(), c.projectRationale(), classification.runId(), classificationAgent.name());
        } else {
            failed.add(classificationAgent.name());
        }

        // Prioridade (política configurada como contexto)
        String summaryText = summary.success() ? summary.output().executiveSummary() : Texts.truncate(d.getObjective(), 1000);
        AgentResult<PriorityAgent.Output> priority = orchestrator.run(priorityAgent, new PriorityAgent.Input(summaryText,
                form.get("impactLevel"), form.get("urgency"), form.get("desiredDate"), d.isRegulatoryRequirement(),
                form.get("estimatedBudget"), d.getDependencies(),
                priorities.findAllByOrderByRankOrderAsc().stream().map(p -> new PriorityAgent.Policy(p.getCode(), p.getName(), p.getPolicy())).toList(),
                LocalDate.now(clock.withZone(ZoneOffset.UTC)).toString()), demandId);
        if (priority.success()) {
            content.put("priority", priority.output());
            suggestIfDifferent(d, DemandField.PRIORITY, priority.output().priority(), priority.output().confidence(),
                    priority.output().rationale(), priority.runId(), priorityAgent.name());
        } else {
            failed.add(priorityAgent.name());
        }

        // Impacto e viabilidade
        AgentResult<ImpactAgent.Output> impact = orchestrator.run(impactAgent, new ImpactAgent.Input(form.get("impactLevel"),
                d.getImpactedAreas(), form.get("impactedUsersCount"), d.getSystemsInvolved(), d.isRegulatoryRequirement(),
                d.getExpectedBenefits()), demandId);
        if (impact.success()) content.put("impact", impact.output()); else failed.add(impactAgent.name());
        content.put("urgency", form.get("urgency"));

        AgentResult<FeasibilityAgent.Output> feasibility = orchestrator.run(feasibilityAgent, new FeasibilityAgent.Input(
                form.get("demandType"), d.getDemandType() != null && d.getDemandType().isTechnical(), d.getScopeDescription(),
                d.getSystemsInvolved(), d.getDependencies(), d.getKnownRisks(), form.get("desiredDate")), demandId);
        if (feasibility.success()) content.put("feasibility", feasibility.output()); else failed.add(feasibilityAgent.name());

        // Completude: determinística (obrigatórios) + qualidade (agente)
        List<String> missingRequired = new ArrayList<>(validator.validate(d).keySet());
        AgentResult<CompletenessAgent.Output> completeness = orchestrator.run(completenessAgent,
                new CompletenessAgent.Input(form, missingRequired), demandId);
        List<Map<String, String>> gaps = new ArrayList<>();
        missingRequired.forEach(f -> gaps.add(Map.of("field", f, "question", "Campo obrigatório não preenchido: "
                + DemandField.byKey(f).map(DemandField::label).orElse(f), "source", "SYSTEM")));
        if (completeness.success()) {
            for (CompletenessAgent.Gap g : completeness.output().gaps()) {
                Map<String, String> gap = new LinkedHashMap<>();
                if (g.field() != null) gap.put("field", g.field());
                gap.put("question", g.question());
                gap.put("source", "AI");
                gaps.add(gap);
                suggestions.create(demandId, completeness.runId(), completenessAgent.name(), AiSuggestion.Kind.MISSING_INFO,
                        g.field(), g.question(), null, 0.6, AiSuggestion.SourceType.ANALYSIS, null,
                        "Lacuna identificada pelo Completeness Agent.");
            }
        } else {
            failed.add(completenessAgent.name());
        }
        content.put("missingInformation", gaps);

        // Duplicidade: candidatos determinísticos + julgamento do agente
        List<DuplicateDetectionAgent.Candidate> candidates = candidateFinder.find(d);
        List<Map<String, Object>> duplicates = new ArrayList<>();
        boolean likelyDuplicate = false;
        if (!candidates.isEmpty()) {
            AgentResult<DuplicateDetectionAgent.Output> dup = orchestrator.run(duplicateAgent,
                    new DuplicateDetectionAgent.Input(d.getTitle(), d.getObjective(), candidates), demandId);
            Map<String, DuplicateDetectionAgent.Verdict> verdicts = dup.success()
                    ? dup.output().verdicts().stream().collect(Collectors.toMap(DuplicateDetectionAgent.Verdict::ref, v -> v, (a, b) -> a))
                    : Map.of();
            if (!dup.success()) failed.add(duplicateAgent.name());
            suggestionRepo.findByDemandIdAndKindAndStatus(demandId, AiSuggestion.Kind.DUPLICATE, AiSuggestion.Status.PENDING)
                    .forEach(s -> s.setStatus(AiSuggestion.Status.SUPERSEDED));
            for (DuplicateDetectionAgent.Candidate c : candidates) {
                DuplicateDetectionAgent.Verdict v = verdicts.get(c.ref());
                boolean likely = v != null && v.likelyDuplicate();
                likelyDuplicate |= likely;
                String rationale = v != null && v.rationale() != null ? v.rationale() : "Similaridade lexical de " + Math.round(c.score() * 100) + "%.";
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("ref", c.ref());
                item.put("title", c.title());
                item.put("score", Math.round(c.score() * 100) / 100.0);
                item.put("likelyDuplicate", likely);
                item.put("rationale", rationale);
                duplicates.add(item);
                suggestions.create(demandId, dup.runId(), duplicateAgent.name(), AiSuggestion.Kind.DUPLICATE, null,
                        c.ref(), c.title(), c.score(), AiSuggestion.SourceType.ANALYSIS, c.ref(),
                        (likely ? "Provável duplicidade. " : "Demanda semelhante. ") + rationale);
            }
        }
        content.put("duplicates", duplicates);

        // Consistência formulário × documentos
        consistency.check(d);

        content.put("recommendation", recommend(missingRequired, likelyDuplicate, classification, d));
        content.put("failedAgents", failed);
        content.put("provider", orchestrator.providerDescription());

        AiAnalysis analysis = new AiAnalysis();
        analysis.setDemandId(demandId);
        analysis.setKind(AiAnalysis.Kind.TRIAGE);
        analysis.setMode(anyMock ? "MOCK" : "REAL");
        analysis.setContent(json.write(content));
        analysis.setCreatedAt(clock.instant());
        analyses.save(analysis);
        audit.event("AI_TRIAGE_COMPLETED").actor("AI:TriageOrchestrator").entity("AiAnalysis", analysis.getId()).demand(demandId)
                .metadata("mode=" + analysis.getMode() + "; failedAgents=" + failed).record();
        return analysis;
    }

    private void suggestIfDifferent(Demand d, DemandField field, String value, double confidence, String rationale, UUID runId, String agent) {
        if (value == null) return;
        String current = accessor.read(d, field);
        if (Objects.equals(current, value)) return;
        suggestions.create(d.getId(), runId, agent, AiSuggestion.Kind.FIELD_VALUE, field.key(), value, current, confidence,
                AiSuggestion.SourceType.ANALYSIS, null, rationale);
    }

    private String recommend(List<String> missingRequired, boolean likelyDuplicate, AgentResult<ClassificationAgent.Output> classification, Demand d) {
        List<String> parts = new ArrayList<>();
        if (!missingRequired.isEmpty()) {
            parts.add("Solicitar ao solicitante as informações obrigatórias ausentes antes de aprovar.");
        }
        if (likelyDuplicate) {
            parts.add("Verificar possível duplicidade com demandas existentes antes de decidir.");
        }
        String typeCode = classification.success() && classification.output().typeCode() != null
                ? classification.output().typeCode() : d.getDemandType() == null ? null : d.getDemandType().getCode();
        demandTypes.findByCode(typeCode == null ? "" : typeCode).ifPresent(t -> parts.add("Encaminhamento sugerido: fluxo \""
                + t.getWorkflow().getName() + "\"" + (t.isTechnical() ? " (demanda técnica: refinamento, arquitetura e repositório de código)." : " (sem execução técnica no repositório).")));
        if (parts.isEmpty()) {
            parts.add("Informação insuficiente para concluir.");
        }
        return String.join(" ", parts);
    }
}
