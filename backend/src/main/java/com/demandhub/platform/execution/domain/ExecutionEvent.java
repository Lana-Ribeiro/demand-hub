package com.demandhub.platform.execution.domain;

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

@Entity
@Table(name = "execution_events")
@Getter
@Setter
@NoArgsConstructor
public class ExecutionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID executionId;
    private String fromStatus;
    private String toStatus;
    private String summary;
    /** GITLAB/GITHUB (webhook real), GITLAB_MOCK/GITHUB_MOCK (simulação explícita) ou PLATFORM. */
    private String source;
    private Instant receivedAt;
}
