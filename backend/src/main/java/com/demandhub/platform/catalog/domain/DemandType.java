package com.demandhub.platform.catalog.domain;

import com.demandhub.platform.workflow.domain.WorkflowDefinition;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tipo de demanda configurável. Determina o workflow (demandas não técnicas não geram issue no repositório de código). */
@Entity
@Table(name = "demand_types")
@Getter
@Setter
@NoArgsConstructor
public class DemandType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;
    private String name;
    private String description;
    private boolean technical;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id")
    private WorkflowDefinition workflow;

    private boolean active = true;
}
