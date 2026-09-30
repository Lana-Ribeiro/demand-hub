package com.demandhub.platform.demand.service;

import com.demandhub.platform.catalog.repository.CatalogRepositories.DemandTypeRepository;
import com.demandhub.platform.catalog.repository.CatalogRepositories.PriorityRepository;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.domain.DemandField;
import com.demandhub.platform.demand.domain.ImpactLevel;
import com.demandhub.platform.demand.domain.Urgency;
import com.demandhub.platform.project.repository.ProjectRepository;
import com.demandhub.platform.shared.error.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Leitura/escrita genérica dos campos da demanda a partir de valores textuais, com validação de tipo e tamanho.
 * Usado pelo formulário, pela edição do PMO e pela aplicação de sugestões aceitas da IA.
 */
@Component
public class DemandFieldAccessor {

    private final ProjectRepository projects;
    private final DemandTypeRepository demandTypes;
    private final PriorityRepository priorities;

    public DemandFieldAccessor(ProjectRepository projects, DemandTypeRepository demandTypes, PriorityRepository priorities) {
        this.projects = projects;
        this.demandTypes = demandTypes;
        this.priorities = priorities;
    }

    public Map<String, String> readAll(Demand d) {
        Map<String, String> values = new LinkedHashMap<>();
        for (DemandField f : DemandField.values()) {
            values.put(f.key(), read(d, f));
        }
        return values;
    }

    public String read(Demand d, DemandField f) {
        return switch (f) {
            case REQUESTER_AREA -> d.getRequesterArea();
            case REQUESTER_MANAGEMENT -> d.getRequesterManagement();
            case REQUESTER_PHONE -> d.getRequesterPhone();
            case SPONSOR_NAME -> d.getSponsorName();
            case TITLE -> d.getTitle();
            case PROJECT -> d.getProject() == null ? null : d.getProject().getCode();
            case DEMAND_TYPE -> d.getDemandType() == null ? null : d.getDemandType().getCode();
            case OBJECTIVE -> d.getObjective();
            case CURRENT_PROBLEM -> d.getCurrentProblem();
            case JUSTIFICATION -> d.getJustification();
            case EXPECTED_BENEFITS -> d.getExpectedBenefits();
            case SCOPE_DESCRIPTION -> d.getScopeDescription();
            case OUT_OF_SCOPE -> d.getOutOfScope();
            case IMPACTED_AREAS -> d.getImpactedAreas();
            case IMPACTED_USERS_COUNT -> d.getImpactedUsersCount() == null ? null : d.getImpactedUsersCount().toString();
            case SYSTEMS_INVOLVED -> d.getSystemsInvolved();
            case IMPACT_LEVEL -> d.getImpactLevel() == null ? null : d.getImpactLevel().name();
            case URGENCY -> d.getUrgency() == null ? null : d.getUrgency().name();
            case DESIRED_DATE -> d.getDesiredDate() == null ? null : d.getDesiredDate().toString();
            case DEADLINE_JUSTIFICATION -> d.getDeadlineJustification();
            case REGULATORY_REQUIREMENT -> Boolean.toString(d.isRegulatoryRequirement());
            case REGULATORY_DESCRIPTION -> d.getRegulatoryDescription();
            case HAS_BUDGET_IMPACT -> Boolean.toString(d.isHasBudgetImpact());
            case ESTIMATED_BUDGET -> d.getEstimatedBudget() == null ? null : d.getEstimatedBudget().toPlainString();
            case COST_CENTER -> d.getCostCenter();
            case BUDGET_APPROVED -> d.getBudgetApproved() == null ? null : d.getBudgetApproved().toString();
            case EXPECTED_RETURN -> d.getExpectedReturn();
            case BUSINESS_FOCAL_POINT -> d.getBusinessFocalPoint();
            case TECHNICAL_FOCAL_POINT -> d.getTechnicalFocalPoint();
            case OTHER_STAKEHOLDERS -> d.getOtherStakeholders();
            case DEPENDENCIES -> d.getDependencies();
            case KNOWN_RISKS -> d.getKnownRisks();
            case ADDITIONAL_NOTES -> d.getAdditionalNotes();
            case PRIORITY -> d.getPriority() == null ? null : d.getPriority().getCode();
        };
    }

    /** Escreve o valor (texto) convertendo para o tipo do campo. Valor vazio limpa o campo. */
    public void write(Demand d, DemandField f, String raw) {
        String v = raw == null || raw.isBlank() ? null : raw.trim();
        if (v != null && v.length() > f.maxLength()) {
            throw invalid(f, "excede " + f.maxLength() + " caracteres");
        }
        switch (f) {
            case REQUESTER_AREA -> d.setRequesterArea(v);
            case REQUESTER_MANAGEMENT -> d.setRequesterManagement(v);
            case REQUESTER_PHONE -> d.setRequesterPhone(v);
            case SPONSOR_NAME -> d.setSponsorName(v);
            case TITLE -> d.setTitle(v);
            case PROJECT -> d.setProject(v == null ? null : projects.findByCodeIgnoreCase(v)
                    .filter(p -> p.isActive()).orElseThrow(() -> invalid(f, "projeto inexistente ou inativo: " + v)));
            case DEMAND_TYPE -> d.setDemandType(v == null ? null : demandTypes.findByCode(v.toUpperCase(Locale.ROOT))
                    .filter(t -> t.isActive()).orElseThrow(() -> invalid(f, "tipo inexistente ou inativo: " + v)));
            case OBJECTIVE -> d.setObjective(v);
            case CURRENT_PROBLEM -> d.setCurrentProblem(v);
            case JUSTIFICATION -> d.setJustification(v);
            case EXPECTED_BENEFITS -> d.setExpectedBenefits(v);
            case SCOPE_DESCRIPTION -> d.setScopeDescription(v);
            case OUT_OF_SCOPE -> d.setOutOfScope(v);
            case IMPACTED_AREAS -> d.setImpactedAreas(v);
            case IMPACTED_USERS_COUNT -> d.setImpactedUsersCount(v == null ? null : parseInt(f, v));
            case SYSTEMS_INVOLVED -> d.setSystemsInvolved(v);
            case IMPACT_LEVEL -> d.setImpactLevel(v == null ? null : parseEnum(f, ImpactLevel.class, v));
            case URGENCY -> d.setUrgency(v == null ? null : parseEnum(f, Urgency.class, v));
            case DESIRED_DATE -> d.setDesiredDate(v == null ? null : parseDate(f, v));
            case DEADLINE_JUSTIFICATION -> d.setDeadlineJustification(v);
            case REGULATORY_REQUIREMENT -> d.setRegulatoryRequirement(v != null && parseBool(f, v));
            case REGULATORY_DESCRIPTION -> d.setRegulatoryDescription(v);
            case HAS_BUDGET_IMPACT -> d.setHasBudgetImpact(v != null && parseBool(f, v));
            case ESTIMATED_BUDGET -> d.setEstimatedBudget(v == null ? null : parseDecimal(f, v));
            case COST_CENTER -> d.setCostCenter(v);
            case BUDGET_APPROVED -> d.setBudgetApproved(v == null ? null : parseBool(f, v));
            case EXPECTED_RETURN -> d.setExpectedReturn(v);
            case BUSINESS_FOCAL_POINT -> d.setBusinessFocalPoint(v);
            case TECHNICAL_FOCAL_POINT -> d.setTechnicalFocalPoint(v);
            case OTHER_STAKEHOLDERS -> d.setOtherStakeholders(v);
            case DEPENDENCIES -> d.setDependencies(v);
            case KNOWN_RISKS -> d.setKnownRisks(v);
            case ADDITIONAL_NOTES -> d.setAdditionalNotes(v);
            case PRIORITY -> d.setPriority(v == null ? null : priorities.findById(v.toUpperCase(Locale.ROOT))
                    .orElseThrow(() -> invalid(f, "prioridade inexistente: " + v)));
        }
    }

    /** Normaliza um valor para comparação/armazenamento (ex.: "1.500,00" → "1500.00"). Lança se inválido. */
    public String normalize(DemandField f, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        return switch (f.type()) {
            case INTEGER -> Integer.toString(parseInt(f, v));
            case DECIMAL -> parseDecimal(f, v).toPlainString();
            case DATE -> parseDate(f, v).toString();
            case BOOLEAN -> Boolean.toString(parseBool(f, v));
            case IMPACT_LEVEL -> parseEnum(f, ImpactLevel.class, v).name();
            case URGENCY -> parseEnum(f, Urgency.class, v).name();
            case DEMAND_TYPE, PRIORITY -> v.toUpperCase(Locale.ROOT);
            case PROJECT -> v.toUpperCase(Locale.ROOT);
            default -> v;
        };
    }

    private static int parseInt(DemandField f, String v) {
        try {
            int n = Integer.parseInt(v.replace(".", "").replace(",", ""));
            if (n < 0) {
                throw invalid(f, "deve ser positivo");
            }
            return n;
        } catch (NumberFormatException e) {
            throw invalid(f, "número inteiro inválido");
        }
    }

    private static BigDecimal parseDecimal(DemandField f, String v) {
        String s = v.replace("R$", "").replace(" ", "");
        if (s.contains(",")) {
            s = s.replace(".", "").replace(",", ".");
        }
        try {
            BigDecimal n = new BigDecimal(s);
            if (n.signum() < 0) {
                throw invalid(f, "deve ser positivo");
            }
            return n.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw invalid(f, "valor numérico inválido");
        }
    }

    private static LocalDate parseDate(DemandField f, String v) {
        try {
            return LocalDate.parse(v.length() > 10 ? v.substring(0, 10) : v);
        } catch (DateTimeParseException e) {
            throw invalid(f, "data inválida (use AAAA-MM-DD)");
        }
    }

    private static boolean parseBool(DemandField f, String v) {
        return switch (v.toLowerCase(Locale.ROOT)) {
            case "true", "sim", "yes", "1" -> true;
            case "false", "nao", "não", "no", "0" -> false;
            default -> throw invalid(f, "valor booleano inválido");
        };
    }

    private static <E extends Enum<E>> E parseEnum(DemandField f, Class<E> type, String v) {
        try {
            return Enum.valueOf(type, v.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw invalid(f, "valor inválido: " + v);
        }
    }

    private static ApiException invalid(DemandField f, String message) {
        return ApiException.businessRule("INVALID_FIELD", f.label() + ": " + message,
                Map.of("fieldErrors", Map.of(f.key(), message)));
    }
}
