package com.demandhub.platform.demand.service;

import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.domain.DemandField;
import com.demandhub.platform.shared.util.Texts;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Regras determinísticas de completude para envio (inclui obrigatoriedade condicional). */
@Component
public class DemandSubmissionValidator {

    private final DemandFieldAccessor accessor;

    public DemandSubmissionValidator(DemandFieldAccessor accessor) {
        this.accessor = accessor;
    }

    /** Campo → mensagem. Vazio = pronto para envio. */
    public Map<String, String> validate(Demand d) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (DemandField f : DemandField.values()) {
            if (f.requiredAtSubmit() && Texts.isBlank(accessor.read(d, f))) {
                errors.put(f.key(), f.label() + " é obrigatório.");
            }
        }
        if (d.getDesiredDate() != null && Texts.isBlank(d.getDeadlineJustification())) {
            errors.put(DemandField.DEADLINE_JUSTIFICATION.key(), "Informe a justificativa do prazo desejado.");
        }
        if (d.isRegulatoryRequirement() && Texts.isBlank(d.getRegulatoryDescription())) {
            errors.put(DemandField.REGULATORY_DESCRIPTION.key(), "Descreva a exigência legal/regulatória.");
        }
        if (d.isHasBudgetImpact()) {
            if (d.getEstimatedBudget() == null) {
                errors.put(DemandField.ESTIMATED_BUDGET.key(), "Informe o orçamento estimado.");
            }
            if (Texts.isBlank(d.getCostCenter())) {
                errors.put(DemandField.COST_CENTER.key(), "Informe o centro de custo.");
            }
        }
        return errors;
    }
}
