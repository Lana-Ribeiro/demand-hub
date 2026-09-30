package com.demandhub.platform.refinement.domain;

import com.demandhub.platform.refinement.domain.RefinementEntities.DecisionType;
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

@Entity
@Table(name = "decisions")
@Getter
@Setter
@NoArgsConstructor
public class Decision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID demandId;
    private UUID meetingId;

    @Enumerated(EnumType.STRING)
    private DecisionType type;

    private String description;
    private String rationale;
    private String decidedBy;
    private UUID createdBy;
    private Instant createdAt;
}
