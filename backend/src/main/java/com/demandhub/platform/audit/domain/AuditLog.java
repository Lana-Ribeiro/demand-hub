package com.demandhub.platform.audit.domain;

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

/** Registro imutável de auditoria. */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID actorId;
    private String actorName;
    private String actorRoles;
    private String action;
    private String entityType;
    private String entityId;
    private UUID demandId;
    private String field;
    private String beforeValue;
    private String afterValue;
    private String reason;
    private UUID aiSuggestionId;
    private String metadata;
    private Instant createdAt;
}
