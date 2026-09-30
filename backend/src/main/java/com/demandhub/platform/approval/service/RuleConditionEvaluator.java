package com.demandhub.platform.approval.service;

import com.demandhub.platform.approval.domain.ApprovalRule;
import com.demandhub.platform.approval.domain.ConditionOperator;
import com.demandhub.platform.demand.domain.Demand;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Avaliação determinística das condições das regras de aprovação.
 * Campos suportados são uma whitelist explícita — nada de expressões arbitrárias.
 */
@Component
public class RuleConditionEvaluator {

    private static final Map<String, Function<Demand, Object>> FIELDS = Map.of(
            "priority", d -> d.getPriority() == null ? null : d.getPriority().getCode(),
            "estimatedBudget", Demand::getEstimatedBudget,
            "impactLevel", d -> d.getImpactLevel() == null ? null : d.getImpactLevel().name(),
            "urgency", d -> d.getUrgency() == null ? null : d.getUrgency().name(),
            "demandType", d -> d.getDemandType() == null ? null : d.getDemandType().getCode(),
            "project", d -> d.getProject() == null ? null : d.getProject().getCode(),
            "regulatoryRequirement", Demand::isRegulatoryRequirement,
            "hasBudgetImpact", Demand::isHasBudgetImpact);

    public static Set<String> supportedFields() {
        return FIELDS.keySet();
    }

    public boolean matches(ApprovalRule rule, Demand demand) {
        if (rule.getProjectId() != null
                && (demand.getProject() == null || !rule.getProjectId().equals(demand.getProject().getId()))) {
            return false;
        }
        if (!rule.hasCondition()) {
            return true;
        }
        Function<Demand, Object> getter = FIELDS.get(rule.getConditionField());
        if (getter == null) {
            // Campo desconhecido: fail-safe — exige aprovação.
            return true;
        }
        return compare(getter.apply(demand), rule.getConditionOperator(), rule.getConditionValue());
    }

    static boolean compare(Object actual, ConditionOperator op, String expected) {
        if (actual == null) {
            return op == ConditionOperator.NEQ;
        }
        if (actual instanceof BigDecimal number) {
            BigDecimal exp;
            try {
                exp = new BigDecimal(expected.trim());
            } catch (NumberFormatException e) {
                return true;
            }
            int c = number.compareTo(exp);
            return switch (op) {
                case EQ -> c == 0;
                case NEQ -> c != 0;
                case GT -> c > 0;
                case GTE -> c >= 0;
                case LT -> c < 0;
                case LTE -> c <= 0;
                case IN -> Arrays.stream(expected.split(",")).map(String::trim).anyMatch(v -> new BigDecimal(v).compareTo(number) == 0);
            };
        }
        String value = actual.toString();
        return switch (op) {
            case EQ -> value.equalsIgnoreCase(Objects.toString(expected, "").trim());
            case NEQ -> !value.equalsIgnoreCase(Objects.toString(expected, "").trim());
            case IN -> Arrays.stream(expected.split(",")).map(String::trim).anyMatch(value::equalsIgnoreCase);
            case GT, GTE, LT, LTE -> {
                int c = value.compareToIgnoreCase(expected.trim());
                yield switch (op) {
                    case GT -> c > 0;
                    case GTE -> c >= 0;
                    case LT -> c < 0;
                    default -> c <= 0;
                };
            }
        };
    }
}
