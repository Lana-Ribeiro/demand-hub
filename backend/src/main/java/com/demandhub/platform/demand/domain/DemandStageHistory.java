package com.demandhub.platform.demand.domain;

import com.demandhub.platform.workflow.domain.StageCategory;
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

/** Permanência em cada estágio — base para tempos médios reais no dashboard. */
@Entity
@Table(name = "demand_stage_history")
@Getter
@Setter
@NoArgsConstructor
public class DemandStageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID demandId;
    private String stageCode;
    private String stageName;

    @Enumerated(EnumType.STRING)
    private StageCategory category;

    private String action;
    private UUID actorId;
    private String actorName;
    private String reason;
    private Instant enteredAt;
    private Instant exitedAt;
}
