package com.demandhub.platform.ai.service;

import com.demandhub.platform.ai.agent.Agent;
import com.demandhub.platform.ai.agent.AgentOrchestrator;
import com.demandhub.platform.ai.agent.AgentResult;
import com.demandhub.platform.ai.agent.AgentSupport.FieldSuggestion;
import com.demandhub.platform.ai.agent.intake.ExtractionAgents;
import com.demandhub.platform.ai.domain.AiSuggestion;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.domain.DemandField;
import com.demandhub.platform.demand.repository.DemandRepositories.DemandRepository;
import com.demandhub.platform.demand.service.DemandFieldAccessor;
import com.demandhub.platform.document.domain.Document;
import com.demandhub.platform.document.repository.DocumentRepository;
import com.demandhub.platform.document.service.DocumentService.DocumentUploaded;
import com.demandhub.platform.shared.util.SystemTasks;
import com.demandhub.platform.shared.util.Texts;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Após o upload: extração por agente (documento ou apresentação), sugestões com origem/confiança,
 * seguida da verificação de consistência formulário × documentos × apresentação.
 */
@Service
public class DocumentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(DocumentAnalysisService.class);

    private final DocumentRepository documents;
    private final DemandRepository demands;
    private final DemandFieldAccessor accessor;
    private final AgentOrchestrator orchestrator;
    private final ExtractionAgents.DocumentExtractionAgent documentAgent;
    private final ExtractionAgents.PresentationExtractionAgent presentationAgent;
    private final AiSuggestionService suggestions;
    private final ConsistencyService consistency;
    private final SystemTasks system;

    public DocumentAnalysisService(DocumentRepository documents, DemandRepository demands, DemandFieldAccessor accessor,
                                   AgentOrchestrator orchestrator, ExtractionAgents.DocumentExtractionAgent documentAgent,
                                   ExtractionAgents.PresentationExtractionAgent presentationAgent, AiSuggestionService suggestions,
                                   ConsistencyService consistency, SystemTasks system) {
        this.documents = documents;
        this.demands = demands;
        this.accessor = accessor;
        this.orchestrator = orchestrator;
        this.documentAgent = documentAgent;
        this.presentationAgent = presentationAgent;
        this.suggestions = suggestions;
        this.consistency = consistency;
        this.system = system;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentUploaded(DocumentUploaded event) {
        if (!event.textExtracted() || event.kind() == Document.Kind.ATTACHMENT) {
            return;
        }
        try {
            system.run(() -> analyze(event.documentId()));
        } catch (RuntimeException e) {
            log.warn("Falha na análise do documento {}: {}", event.documentId(), e.getMessage());
        }
    }

    /** Pode ser reexecutado manualmente. Retorna o número de sugestões criadas. */
    public int analyze(UUID documentId) {
        Document doc = documents.findById(documentId).orElseThrow();
        Demand demand = demands.findById(doc.getDemandId()).orElseThrow();
        boolean presentation = doc.getKind() == Document.Kind.PRESENTATION;
        Agent<ExtractionAgents.Input, ExtractionAgents.Output> agent = presentation ? presentationAgent : documentAgent;
        AgentResult<ExtractionAgents.Output> result = orchestrator.run(agent,
                new ExtractionAgents.Input(doc.getFileName(), doc.getExtractedText()), demand.getId());
        if (!result.success()) {
            return 0;
        }
        AiSuggestion.SourceType source = presentation ? AiSuggestion.SourceType.PRESENTATION : AiSuggestion.SourceType.DOCUMENT;
        int created = 0;
        for (FieldSuggestion fs : result.output().suggestions()) {
            DemandField field = DemandField.byKey(fs.field()).orElse(null);
            if (field == null) continue;
            String normalized;
            try {
                normalized = accessor.normalize(field, fs.value());
            } catch (RuntimeException invalid) {
                continue; // valor que não passa na validação do campo não vira sugestão
            }
            String current = accessor.read(demand, field);
            String rationale = (fs.rationale() == null ? "" : fs.rationale() + " ")
                    + (fs.excerpt() == null ? "" : "Trecho: \"" + Texts.truncate(fs.excerpt(), 300) + "\"");
            suggestions.create(demand.getId(), result.runId(), agent.name(), AiSuggestion.Kind.FIELD_VALUE, field.key(),
                    normalized, current, fs.confidence(), source, doc.getId().toString(),
                    "Origem: " + (presentation ? "apresentação" : "documento") + " \"" + doc.getFileName() + "\". " + rationale.trim());
            created++;
        }
        consistency.check(demand);
        return created;
    }
}
