package com.demandhub.platform.ai.service;

import com.demandhub.platform.ai.domain.AiSuggestion;
import com.demandhub.platform.ai.repository.AiRepositories.AiSuggestionRepository;
import com.demandhub.platform.audit.service.AuditService;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.domain.DemandField;
import com.demandhub.platform.demand.service.DemandService;
import com.demandhub.platform.refinement.domain.RefinementEntities.DecisionType;
import com.demandhub.platform.refinement.domain.RefinementEntities.ItemOrigin;
import com.demandhub.platform.refinement.domain.RefinementEntities.ItemType;
import com.demandhub.platform.refinement.service.RefinementService;
import com.demandhub.platform.shared.error.ApiException;
import com.demandhub.platform.shared.security.CurrentUser;
import com.demandhub.platform.shared.security.Permissions;
import com.demandhub.platform.shared.util.Texts;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sugestões da IA e decisões humanas sobre elas. Nenhuma sugestão altera dados sem ACCEPT/EDIT explícito;
 * toda decisão é auditada com referência à sugestão.
 */
@Service
public class AiSuggestionService {

    public enum DecisionAction { ACCEPT, EDIT, REJECT }

    private final AiSuggestionRepository suggestions;
    private final DemandService demandService;
    private final RefinementService refinementService;
    private final AuditService audit;
    private final Clock clock;

    public AiSuggestionService(AiSuggestionRepository suggestions, DemandService demandService,
                               RefinementService refinementService, AuditService audit, Clock clock) {
        this.suggestions = suggestions;
        this.demandService = demandService;
        this.refinementService = refinementService;
        this.audit = audit;
        this.clock = clock;
    }

    /** Cria sugestão PENDING, substituindo pendentes equivalentes (mesmo tipo, campo e origem). */
    @Transactional
    public AiSuggestion create(UUID demandId, UUID runId, String agent, AiSuggestion.Kind kind, String field,
                               String suggestedValue, String alternativeValue, double confidence,
                               AiSuggestion.SourceType source, String sourceRef, String rationale) {
        if (kind == AiSuggestion.Kind.FIELD_VALUE || kind == AiSuggestion.Kind.MISSING_INFO) {
            suggestions.findByDemandIdAndKindAndSourceTypeAndStatus(demandId, kind, source, AiSuggestion.Status.PENDING).stream()
                    .filter(s -> java.util.Objects.equals(field, s.getField()))
                    .filter(s -> sourceRef == null || sourceRef.equals(s.getSourceRef()))
                    .forEach(s -> s.setStatus(AiSuggestion.Status.SUPERSEDED));
        }
        AiSuggestion s = new AiSuggestion();
        s.setDemandId(demandId);
        s.setAgentRunId(runId);
        s.setAgent(agent);
        s.setKind(kind);
        s.setField(field);
        s.setSuggestedValue(Texts.truncate(suggestedValue, 8000));
        s.setAlternativeValue(Texts.truncate(alternativeValue, 8000));
        s.setConfidence(BigDecimal.valueOf(confidence).setScale(3, RoundingMode.HALF_UP));
        s.setSourceType(source);
        s.setSourceRef(sourceRef);
        s.setRationale(Texts.truncate(rationale, 4000));
        s.setCreatedAt(clock.instant());
        return suggestions.save(s);
    }

    @Transactional(readOnly = true)
    public List<AiSuggestion> list(UUID demandId, boolean onlyPending, CurrentUser user) {
        demandService.getForView(demandId, user);
        return onlyPending ? suggestions.findByDemandIdAndStatusOrderByCreatedAtDesc(demandId, AiSuggestion.Status.PENDING)
                : suggestions.findByDemandIdOrderByCreatedAtDesc(demandId);
    }

    @Transactional
    public AiSuggestion decide(UUID demandId, UUID suggestionId, DecisionAction action, String editedValue, String reason, CurrentUser user) {
        AiSuggestion s = suggestions.findById(suggestionId).filter(x -> x.getDemandId().equals(demandId))
                .orElseThrow(() -> ApiException.notFound("Sugestão", suggestionId));
        if (s.getStatus() != AiSuggestion.Status.PENDING) {
            throw ApiException.businessRule("SUGGESTION_NOT_PENDING", "Esta sugestão já foi decidida.");
        }
        Demand demand = demandService.getForView(demandId, user);
        if (action == DecisionAction.EDIT && Texts.isBlank(editedValue)) {
            throw ApiException.businessRule("EDITED_VALUE_REQUIRED", "Informe o valor editado.");
        }
        String finalValue = switch (action) {
            case ACCEPT -> s.getSuggestedValue();
            case EDIT -> editedValue.trim();
            case REJECT -> null;
        };
        if (action != DecisionAction.REJECT) {
            apply(demand, s, finalValue, reason, user);
        } else if (!demand.isOwnedBy(user.id()) && !user.has(Permissions.DEMAND_VIEW_ALL)) {
            throw ApiException.forbidden("Sem permissão para decidir esta sugestão.");
        }
        s.setStatus(switch (action) {
            case ACCEPT -> AiSuggestion.Status.ACCEPTED;
            case EDIT -> AiSuggestion.Status.EDITED;
            case REJECT -> AiSuggestion.Status.REJECTED;
        });
        s.setFinalValue(Texts.truncate(finalValue, 8000));
        s.setDecidedBy(user.id());
        s.setDecidedAt(clock.instant());
        s.setDecisionReason(reason);
        audit.event("AI_SUGGESTION_DECIDED").entity("AiSuggestion", s.getId()).demand(demandId)
                .change(s.getField() == null ? s.getKind().name() : s.getField(), s.getSuggestedValue(), finalValue)
                .reason(reason).aiSuggestion(s.getId())
                .metadata("agent=" + s.getAgent() + "; decision=" + action + "; confidence=" + s.getConfidence()).record();
        return s;
    }

    private void apply(Demand demand, AiSuggestion s, String value, String reason, CurrentUser user) {
        switch (s.getKind()) {
            case FIELD_VALUE, INCONSISTENCY -> {
                DemandField field = DemandField.byKey(s.getField())
                        .orElseThrow(() -> ApiException.businessRule("INVALID_SUGGESTION", "Campo da sugestão inválido."));
                demandService.changeField(demand, field, value, reasonOr(reason, "Sugestão da IA (" + s.getAgent() + ")"), s.getId(), user);
            }
            case REFINEMENT_ITEM -> {
                if (s.getField() != null && s.getField().startsWith("DECISION:")) {
                    DecisionType type = DecisionType.valueOf(s.getField().substring("DECISION:".length()));
                    refinementService.addDecision(demand.getId(), null, type, value, "Proposta do Refinement Agent aceita.", null, user);
                } else {
                    refinementService.addItem(demand.getId(), ItemType.valueOf(s.getField()), value, ItemOrigin.AI_ACCEPTED, user);
                }
            }
            case MISSING_INFO -> {
                // No rascunho, o solicitante apenas reconhece a lacuna; após o envio, o PMO a converte em pendência formal.
                if (!demand.isDraft() && user.has(Permissions.DEMAND_TRIAGE)) {
                    demandService.requestInformation(demand.getId(), value, user);
                } else if (!demand.isOwnedBy(user.id()) && !user.has(Permissions.DEMAND_VIEW_ALL)) {
                    throw ApiException.forbidden("Sem permissão para decidir esta sugestão.");
                }
            }
            case DUPLICATE -> {
                if (!user.has(Permissions.DEMAND_TRIAGE)) {
                    throw ApiException.forbidden("Somente o PMO confirma duplicidades.");
                }
            }
        }
    }

    private static String reasonOr(String reason, String fallback) {
        return Texts.isBlank(reason) ? fallback : reason;
    }
}
