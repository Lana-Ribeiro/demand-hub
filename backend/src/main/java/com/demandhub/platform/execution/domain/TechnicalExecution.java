package com.demandhub.platform.execution.domain;

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

/** Execução técnica — lifecycle próprio (fonte: repositório de código GitLab/GitHub), separado do lifecycle da demanda. */
@Entity
@Table(name = "technical_executions")
@Getter
@Setter
@NoArgsConstructor
public class TechnicalExecution {

    public enum Strategy { TECHNICAL_PROMPT, AGENT_SQUAD }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID demandId;
    /** GITLAB ou GITHUB. */
    private String system = "GITLAB";

    @Enumerated(EnumType.STRING)
    private Strategy strategy = Strategy.TECHNICAL_PROMPT;

    private String statusCode;
    private UUID externalLinkId;
    private Instant startedAt;
    private Instant completedAt;
    private Instant lastEventAt;
}
