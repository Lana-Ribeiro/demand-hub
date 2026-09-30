package com.demandhub.platform.approval.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Regra configurável de approval gate: ao entrar no estágio {@code stageCode}, se a condição
 * (opcional) for verdadeira, exige aprovação do papel {@code approverRole}.
 */
@Entity
@Table(name = "approval_rules")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long workflowId;
    private String stageCode;
    private String name;
    private String approverRole;
    private String conditionField;

    @Enumerated(EnumType.STRING)
    private ConditionOperator conditionOperator;

    private String conditionValue;
    /** Restringe a regra a um projeto (nulo = todos). */
    private Long projectId;
    private boolean active = true;
    private int position;

    public boolean hasCondition() {
        return conditionField != null && !conditionField.isBlank() && conditionOperator != null;
    }
}
