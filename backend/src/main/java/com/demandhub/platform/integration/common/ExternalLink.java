package com.demandhub.platform.integration.common;

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

/** Vínculo da demanda com um item externo (Jira PMO, GitLab ou GitHub). {@code mode} deixa explícito se é REAL ou MOCK. */
@Entity
@Table(name = "external_links")
@Getter
@Setter
@NoArgsConstructor
public class ExternalLink {

    public enum System { JIRA, GITLAB, GITHUB }

    public enum SyncStatus { OK, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID demandId;

    @Enumerated(EnumType.STRING)
    private System system;

    @Enumerated(EnumType.STRING)
    private IntegrationMode mode;

    private String externalId;
    private String externalKey;
    private String projectRef;
    private String url;
    /** Item do card no board (GitHub Projects), quando configurado. */
    private String boardItemId;

    @Enumerated(EnumType.STRING)
    private SyncStatus syncStatus;

    private String lastError;
    private Instant lastSyncAt;
    private Instant createdAt;

    public boolean isCreated() {
        return externalKey != null;
    }
}
