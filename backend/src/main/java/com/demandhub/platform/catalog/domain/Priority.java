package com.demandhub.platform.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "priorities")
@Getter
@Setter
@NoArgsConstructor
public class Priority {

    @Id
    private String code;

    private String name;

    @Column(name = "rank_order")
    private int rankOrder;

    private String color;

    /** Política textual — contexto para o Priority Agent; decisão final é humana. */
    private String policy;
}
