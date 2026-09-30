package com.demandhub.platform.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Sugestão da IA aguardando decisão humana (aceitar, editar, rejeitar). */
@Entity
@Table(name = "ai_suggestions")
@Getter
@Setter
@NoArgsConstructor
public class AiSuggestion {

    public enum Kind { FIELD_VALUE, MISSING_INFO, INCONSISTENCY, DUPLICATE, REFINEMENT_ITEM }

    public enum SourceType { FORM, DOCUMENT, PRESENTATION, CHAT, ANALYSIS, MEETING }

    public enum Status { PENDING, ACCEPTED, EDITED, REJECTED, SUPERSEDED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID demandId;
    private UUID agentRunId;
    private String agent;

    @Enumerated(EnumType.STRING)
    private Kind kind;

    private String field;
    private String suggestedValue;
    private String alternativeValue;
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    private SourceType sourceType;

    private String sourceRef;
    private String rationale;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    private String finalValue;
    private UUID decidedBy;
    private Instant decidedAt;
    private String decisionReason;
    private Instant createdAt;
}
