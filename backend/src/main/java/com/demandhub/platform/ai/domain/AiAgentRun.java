package com.demandhub.platform.ai.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Registro de cada execução de agente (entrada mínima, saída, provedor, modo, duração) — rastreabilidade. */
@Entity
@Table(name = "ai_agent_runs")
@Getter
@Setter
@NoArgsConstructor
public class AiAgentRun {

    public enum Status { SUCCESS, FAILED, INVALID_OUTPUT }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID demandId;
    private String agent;
    private String provider;
    private String model;
    private String mode;
    private String input;
    private String output;
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Status status;
    private String error;
    private Long durationMs;
    private UUID createdBy;
    private Instant createdAt;
}
