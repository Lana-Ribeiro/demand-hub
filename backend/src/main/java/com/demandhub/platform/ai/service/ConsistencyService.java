package com.demandhub.platform.ai.service;

import com.demandhub.platform.ai.domain.AiSuggestion;
import com.demandhub.platform.ai.repository.AiRepositories.AiSuggestionRepository;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.domain.DemandField;
import com.demandhub.platform.demand.service.DemandFieldAccessor;
import com.demandhub.platform.shared.util.Texts;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consistency Agent (estratégia determinística): compara valores extraídos de documentos/apresentações com o formulário
 * e entre si. Divergências viram sugestões INCONSISTENCY — o cliente ou o PMO decide qual valor é válido.
 */
@Service
public class ConsistencyService {

    public static final String AGENT = "ConsistencyAgent";

    private final AiSuggestionRepository suggestionRepo;
    private final AiSuggestionService suggestions;
    private final DemandFieldAccessor accessor;

    public ConsistencyService(AiSuggestionRepository suggestionRepo, AiSuggestionService suggestions, DemandFieldAccessor accessor) {
        this.suggestionRepo = suggestionRepo;
        this.suggestions = suggestions;
        this.accessor = accessor;
    }

    @Transactional
    public int check(Demand demand) {
        UUID demandId = demand.getId();
        suggestionRepo.findByDemandIdAndKindAndStatus(demandId, AiSuggestion.Kind.INCONSISTENCY, AiSuggestion.Status.PENDING)
                .forEach(s -> s.setStatus(AiSuggestion.Status.SUPERSEDED));
        List<AiSuggestion> extracted = suggestionRepo.findByDemandIdAndSourceTypeInAndStatus(demandId,
                EnumSet.of(AiSuggestion.SourceType.DOCUMENT, AiSuggestion.SourceType.PRESENTATION), AiSuggestion.Status.PENDING);
        int created = 0;

        // 1) Extraído × formulário
        for (AiSuggestion s : extracted) {
            DemandField field = DemandField.byKey(s.getField()).orElse(null);
            if (field == null) continue;
            String formValue = accessor.read(demand, field);
            if (Texts.isBlank(formValue) || equivalent(field, formValue, s.getSuggestedValue())) continue;
            suggestions.create(demandId, s.getAgentRunId(), AGENT, AiSuggestion.Kind.INCONSISTENCY, field.key(),
                    s.getSuggestedValue(), formValue, s.getConfidence().doubleValue(), s.getSourceType(), s.getSourceRef(),
                    "Divergência: formulário = \"" + Texts.truncate(formValue, 200) + "\"; " + label(s.getSourceType())
                            + " = \"" + Texts.truncate(s.getSuggestedValue(), 200) + "\". Decida qual informação é válida.");
            created++;
        }

        // 2) Documento × apresentação (quando o formulário ainda não tem o valor)
        Map<String, List<AiSuggestion>> byField = extracted.stream().collect(Collectors.groupingBy(AiSuggestion::getField));
        for (Map.Entry<String, List<AiSuggestion>> e : byField.entrySet()) {
            DemandField field = DemandField.byKey(e.getKey()).orElse(null);
            if (field == null || !Texts.isBlank(accessor.read(demand, field))) continue;
            AiSuggestion doc = e.getValue().stream().filter(s -> s.getSourceType() == AiSuggestion.SourceType.DOCUMENT).findFirst().orElse(null);
            AiSuggestion ppt = e.getValue().stream().filter(s -> s.getSourceType() == AiSuggestion.SourceType.PRESENTATION).findFirst().orElse(null);
            if (doc != null && ppt != null && !equivalent(field, doc.getSuggestedValue(), ppt.getSuggestedValue())) {
                suggestions.create(demandId, ppt.getAgentRunId(), AGENT, AiSuggestion.Kind.INCONSISTENCY, field.key(),
                        ppt.getSuggestedValue(), doc.getSuggestedValue(), ppt.getConfidence().doubleValue(),
                        AiSuggestion.SourceType.PRESENTATION, ppt.getSourceRef(),
                        "Divergência entre documentação (\"" + Texts.truncate(doc.getSuggestedValue(), 200) + "\") e apresentação (\""
                                + Texts.truncate(ppt.getSuggestedValue(), 200) + "\").");
                created++;
            }
        }
        return created;
    }

    static boolean equivalent(DemandField field, String a, String b) {
        if (Texts.isBlank(a) || Texts.isBlank(b)) {
            return Texts.isBlank(a) == Texts.isBlank(b);
        }
        try {
            switch (field.type()) {
                case DATE:
                    return YearMonth.from(LocalDate.parse(a.substring(0, 10))).equals(YearMonth.from(LocalDate.parse(b.substring(0, 10))));
                case DECIMAL:
                case INTEGER:
                    return new BigDecimal(numeric(a)).compareTo(new BigDecimal(numeric(b))) == 0;
                default:
                    break;
            }
        } catch (RuntimeException ignored) {
            // cai na comparação textual
        }
        String na = Texts.normalize(a);
        String nb = Texts.normalize(b);
        if (na.equals(nb) || na.contains(nb) || nb.contains(na)) {
            return true;
        }
        return Texts.jaccard(Texts.tokens(a), Texts.tokens(b)) >= 0.6;
    }

    private static String numeric(String s) {
        String v = s.replace("R$", "").replace(" ", "");
        return v.contains(",") ? v.replace(".", "").replace(",", ".") : v;
    }

    private static String label(AiSuggestion.SourceType t) {
        return t == AiSuggestion.SourceType.PRESENTATION ? "apresentação" : "documentação";
    }
}
