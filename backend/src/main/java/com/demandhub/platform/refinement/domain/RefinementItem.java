package com.demandhub.platform.refinement.domain;

import com.demandhub.platform.refinement.domain.RefinementEntities.ItemOrigin;
import com.demandhub.platform.refinement.domain.RefinementEntities.ItemStatus;
import com.demandhub.platform.refinement.domain.RefinementEntities.ItemType;
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

/** Requisito, critério de aceite, dependência, risco, dúvida ou pendência do refinamento. */
@Entity
@Table(name = "refinement_items")
@Getter
@Setter
@NoArgsConstructor
public class RefinementItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID demandId;

    @Enumerated(EnumType.STRING)
    private ItemType type;

    private String description;

    @Enumerated(EnumType.STRING)
    private ItemStatus status = ItemStatus.OPEN;

    @Enumerated(EnumType.STRING)
    private ItemOrigin origin = ItemOrigin.HUMAN;

    private UUID createdBy;
    private Instant createdAt;
    private Instant resolvedAt;
}
