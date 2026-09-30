package com.demandhub.platform.demand.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Catálogo dos campos do formulário "Abertura de Iniciativas — Diretoria Transformação de Redes".
 * Fonte única para: validação, aplicação genérica de valores (inclusive sugestões aceitas da IA),
 * whitelist de campos que agentes podem sugerir e textos de ajuda do assistente.
 * Obrigatoriedade condicional está em {@code DemandSubmissionValidator}.
 */
public enum DemandField {

    // Solicitante
    REQUESTER_AREA("requesterArea", "Diretoria / área solicitante", Section.REQUESTER, FieldType.TEXT, true, 200,
            "Diretoria ou área de negócio que solicita a iniciativa."),
    REQUESTER_MANAGEMENT("requesterManagement", "Gerência", Section.REQUESTER, FieldType.TEXT, false, 200,
            "Gerência responsável dentro da área solicitante."),
    REQUESTER_PHONE("requesterPhone", "Telefone de contato", Section.REQUESTER, FieldType.TEXT, false, 40,
            "Telefone para contato sobre a demanda."),
    SPONSOR_NAME("sponsorName", "Patrocinador", Section.REQUESTER, FieldType.TEXT, true, 200,
            "Executivo que patrocina a iniciativa e responde por ela."),

    // Iniciativa
    TITLE("title", "Título da iniciativa", Section.INITIATIVE, FieldType.TEXT, true, 200,
            "Nome curto e descritivo da iniciativa."),
    PROJECT("project", "Projeto", Section.INITIATIVE, FieldType.PROJECT, false, 40,
            "Projeto ao qual a iniciativa pertence. Se não souber, o PMO definirá na triagem."),
    DEMAND_TYPE("demandType", "Tipo de demanda", Section.INITIATIVE, FieldType.DEMAND_TYPE, true, 40,
            "Natureza da demanda: desenvolvimento, melhoria, automação, integração, alocação de recurso, mudança operacional, consultoria, suporte ou outro."),
    OBJECTIVE("objective", "Objetivo da iniciativa", Section.INITIATIVE, FieldType.LONG_TEXT, true, 8000,
            "O que se pretende alcançar com a iniciativa."),
    CURRENT_PROBLEM("currentProblem", "Problema / situação atual", Section.INITIATIVE, FieldType.LONG_TEXT, true, 8000,
            "Qual problema existe hoje e como o processo funciona atualmente."),
    JUSTIFICATION("justification", "Justificativa", Section.INITIATIVE, FieldType.LONG_TEXT, true, 8000,
            "Por que a iniciativa é necessária agora."),
    EXPECTED_BENEFITS("expectedBenefits", "Benefícios esperados", Section.INITIATIVE, FieldType.LONG_TEXT, true, 8000,
            "Resultados esperados: ganhos financeiros, operacionais, de qualidade ou de conformidade."),
    SCOPE_DESCRIPTION("scopeDescription", "Escopo", Section.INITIATIVE, FieldType.LONG_TEXT, false, 8000,
            "O que está incluído na iniciativa."),
    OUT_OF_SCOPE("outOfScope", "Fora do escopo", Section.INITIATIVE, FieldType.LONG_TEXT, false, 8000,
            "O que explicitamente não faz parte da iniciativa."),

    // Impacto e prazo
    IMPACTED_AREAS("impactedAreas", "Áreas impactadas", Section.IMPACT, FieldType.TEXT, true, 1000,
            "Áreas, equipes ou processos afetados."),
    IMPACTED_USERS_COUNT("impactedUsersCount", "Quantidade estimada de usuários impactados", Section.IMPACT, FieldType.INTEGER, false, 10,
            "Número aproximado de pessoas que utilizam ou são afetadas pelo processo."),
    SYSTEMS_INVOLVED("systemsInvolved", "Sistemas envolvidos", Section.IMPACT, FieldType.TEXT, false, 1000,
            "Sistemas atuais relacionados à iniciativa."),
    IMPACT_LEVEL("impactLevel", "Nível de impacto", Section.IMPACT, FieldType.IMPACT_LEVEL, true, 20,
            "LOW (baixo), MEDIUM (médio), HIGH (alto) ou CRITICAL (crítico)."),
    URGENCY("urgency", "Urgência", Section.IMPACT, FieldType.URGENCY, true, 20,
            "LOW (baixa), MEDIUM (média) ou HIGH (alta)."),
    DESIRED_DATE("desiredDate", "Prazo desejado", Section.IMPACT, FieldType.DATE, false, 10,
            "Data desejada para entrega (AAAA-MM-DD)."),
    DEADLINE_JUSTIFICATION("deadlineJustification", "Justificativa do prazo", Section.IMPACT, FieldType.LONG_TEXT, false, 2000,
            "Motivo do prazo (obrigatório quando há prazo desejado)."),
    REGULATORY_REQUIREMENT("regulatoryRequirement", "Possui exigência legal/regulatória?", Section.IMPACT, FieldType.BOOLEAN, false, 5,
            "Indica se a iniciativa atende a obrigação legal, regulatória ou contratual."),
    REGULATORY_DESCRIPTION("regulatoryDescription", "Descrição da exigência regulatória", Section.IMPACT, FieldType.LONG_TEXT, false, 2000,
            "Norma, órgão e prazo da exigência (obrigatório quando há exigência regulatória)."),

    // Financeiro
    HAS_BUDGET_IMPACT("hasBudgetImpact", "Possui impacto orçamentário?", Section.FINANCIAL, FieldType.BOOLEAN, false, 5,
            "Indica se a iniciativa envolve custo, investimento ou retorno financeiro."),
    ESTIMATED_BUDGET("estimatedBudget", "Orçamento estimado (R$)", Section.FINANCIAL, FieldType.DECIMAL, false, 20,
            "Valor estimado de investimento (obrigatório quando há impacto orçamentário)."),
    COST_CENTER("costCenter", "Centro de custo", Section.FINANCIAL, FieldType.TEXT, false, 100,
            "Centro de custo responsável (obrigatório quando há impacto orçamentário)."),
    BUDGET_APPROVED("budgetApproved", "Orçamento já aprovado?", Section.FINANCIAL, FieldType.BOOLEAN, false, 5,
            "Indica se o orçamento já está aprovado."),
    EXPECTED_RETURN("expectedReturn", "Retorno financeiro esperado", Section.FINANCIAL, FieldType.LONG_TEXT, false, 2000,
            "Economia ou receita esperada, se houver."),

    // Envolvidos
    BUSINESS_FOCAL_POINT("businessFocalPoint", "Ponto focal de negócio", Section.STAKEHOLDERS, FieldType.TEXT, true, 200,
            "Pessoa de referência do negócio para dúvidas e validações."),
    TECHNICAL_FOCAL_POINT("technicalFocalPoint", "Ponto focal técnico", Section.STAKEHOLDERS, FieldType.TEXT, false, 200,
            "Pessoa de referência técnica, se houver."),
    OTHER_STAKEHOLDERS("otherStakeholders", "Outros envolvidos", Section.STAKEHOLDERS, FieldType.LONG_TEXT, false, 2000,
            "Demais pessoas ou áreas envolvidas."),

    // Complementos
    DEPENDENCIES("dependencies", "Dependências", Section.COMPLEMENTS, FieldType.LONG_TEXT, false, 8000,
            "Outras iniciativas, sistemas ou decisões das quais esta depende."),
    KNOWN_RISKS("knownRisks", "Riscos conhecidos", Section.COMPLEMENTS, FieldType.LONG_TEXT, false, 8000,
            "Riscos já identificados."),
    ADDITIONAL_NOTES("additionalNotes", "Observações", Section.COMPLEMENTS, FieldType.LONG_TEXT, false, 8000,
            "Informações adicionais relevantes."),

    // Classificação (triagem PMO — não editável pelo cliente)
    PRIORITY("priority", "Prioridade", Section.CLASSIFICATION, FieldType.PRIORITY, false, 10,
            "Prioridade definida pelo PMO (P1 a P4).");

    public enum Section { REQUESTER, INITIATIVE, IMPACT, FINANCIAL, STAKEHOLDERS, COMPLEMENTS, CLASSIFICATION }

    public enum FieldType { TEXT, LONG_TEXT, INTEGER, DECIMAL, DATE, BOOLEAN, IMPACT_LEVEL, URGENCY, PROJECT, DEMAND_TYPE, PRIORITY }

    private final String key;
    private final String label;
    private final Section section;
    private final FieldType type;
    private final boolean requiredAtSubmit;
    private final int maxLength;
    private final String help;

    DemandField(String key, String label, Section section, FieldType type, boolean requiredAtSubmit, int maxLength, String help) {
        this.key = key;
        this.label = label;
        this.section = section;
        this.type = type;
        this.requiredAtSubmit = requiredAtSubmit;
        this.maxLength = maxLength;
        this.help = help;
    }

    public String key() { return key; }
    public String label() { return label; }
    public Section section() { return section; }
    public FieldType type() { return type; }
    public boolean requiredAtSubmit() { return requiredAtSubmit; }
    public int maxLength() { return maxLength; }
    public String help() { return help; }

    /** Campos que o solicitante preenche (a prioridade é exclusiva do PMO). */
    public boolean clientEditable() {
        return section != Section.CLASSIFICATION;
    }

    public static Optional<DemandField> byKey(String key) {
        return Arrays.stream(values()).filter(f -> f.key.equals(key)).findFirst();
    }
}
