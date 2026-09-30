package com.demandhub.platform.workflow.domain;

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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "workflow_stages")
@Getter
@Setter
@NoArgsConstructor
public class WorkflowStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id")
    private WorkflowDefinition workflow;

    private String code;
    private String name;

    @Enumerated(EnumType.STRING)
    private StageCategory category;

    private int position;
    private boolean autoAdvance;
    /** Nome do status correspondente no Jira PMO (configurável). */
    private String jiraStatus;
    /** CSV de {@link StageAction}. */
    private String onEnterActions;
    /** CSV de códigos de {@code ExitRequirementChecker}. */
    private String exitRequirements;

    public Set<StageAction> onEnterActionSet() {
        return csv(onEnterActions).stream().map(StageAction::valueOf).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<String> exitRequirementSet() {
        return csv(exitRequirements);
    }

    private static Set<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
