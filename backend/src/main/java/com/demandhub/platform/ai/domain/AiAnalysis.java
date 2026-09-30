package com.demandhub.platform.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Análise estruturada gerada por agente. A versão exibida é editedContent (humano) ou content (IA). */
@Entity
@Table(name = "ai_analyses")
@Getter
@Setter
@NoArgsConstructor
public class AiAnalysis {

    public enum Kind { TRIAGE, ARCHITECTURE, TECH_SPEC, TECH_PROMPT, SQUAD_PLAN, PROGRESS, TECHNICAL_DOC, CLIENT_DOC }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID demandId;
    private UUID agentRunId;

    @Enumerated(EnumType.STRING)
    private Kind kind;

    private String mode;
    private String content;
    private String editedContent;
    private UUID editedBy;
    private Instant editedAt;
    private Instant createdAt;

    public String effectiveContent() {
        return editedContent != null ? editedContent : content;
    }
}
