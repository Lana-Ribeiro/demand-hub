package com.demandhub.platform.demand.domain;

import com.demandhub.platform.catalog.domain.DemandType;
import com.demandhub.platform.catalog.domain.Priority;
import com.demandhub.platform.identity.domain.User;
import com.demandhub.platform.project.domain.Project;
import com.demandhub.platform.workflow.domain.WorkflowDefinition;
import com.demandhub.platform.workflow.domain.WorkflowStage;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Entidade principal de negócio. O estágio só é alterado pelo {@code WorkflowEngine};
 * campos do formulário só pelo {@code DemandService} (com auditoria).
 */
@Entity
@Table(name = "demands")
@Getter
@Setter
@NoArgsConstructor
public class Demand {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String protocol;
    private String title;

    @Enumerated(EnumType.STRING)
    private DemandSource source = DemandSource.PORTAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id")
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demand_type_id")
    private DemandType demandType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "priority_code")
    private Priority priority;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id")
    private WorkflowDefinition workflow;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_stage_id")
    private WorkflowStage currentStage;

    @Enumerated(EnumType.STRING)
    private LifecycleState lifecycleState = LifecycleState.DRAFT;

    private boolean readOnly;

    // Solicitante
    private String requesterArea;
    private String requesterManagement;
    private String requesterPhone;
    private String sponsorName;

    // Iniciativa
    private String objective;
    private String currentProblem;
    private String justification;
    private String expectedBenefits;
    private String scopeDescription;
    private String outOfScope;

    // Impacto e prazo
    private String impactedAreas;
    private Integer impactedUsersCount;
    private String systemsInvolved;
    @Enumerated(EnumType.STRING)
    private ImpactLevel impactLevel;
    @Enumerated(EnumType.STRING)
    private Urgency urgency;
    private LocalDate desiredDate;
    private String deadlineJustification;
    private boolean regulatoryRequirement;
    private String regulatoryDescription;

    // Financeiro (condicional)
    private boolean hasBudgetImpact;
    private BigDecimal estimatedBudget;
    private String costCenter;
    private Boolean budgetApproved;
    private String expectedReturn;

    // Envolvidos
    private String businessFocalPoint;
    private String technicalFocalPoint;
    private String otherStakeholders;

    // Complementos
    private String dependencies;
    private String knownRisks;
    private String additionalNotes;
    /** JSON livre para campos adicionais do formulário corporativo (sem migração). */
    private String additionalData;

    // Legado (sem histórico inventado)
    private String originalId;
    private Instant originalCreatedAt;
    private String originalStatus;
    private String originalOwner;

    private Instant submittedAt;
    private Instant completedAt;
    private Instant stageEnteredAt;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Version
    private long version;

    public boolean isOwnedBy(UUID userId) {
        return requester != null && requester.getId().equals(userId);
    }

    public boolean isDraft() {
        return lifecycleState == LifecycleState.DRAFT;
    }

    public String displayId() {
        return protocol != null ? protocol : "Rascunho";
    }
}
