package com.demandhub.platform.workflow.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "workflow_definitions")
@Getter
@Setter
@NoArgsConstructor
public class WorkflowDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;
    private String name;
    private String description;
    private boolean active = true;

    @OneToMany(mappedBy = "workflow")
    @OrderBy("position ASC")
    private List<WorkflowStage> stages = new ArrayList<>();

    @OneToMany(mappedBy = "workflow")
    private List<WorkflowTransition> transitions = new ArrayList<>();

    public Optional<WorkflowStage> stage(String code) {
        return stages.stream().filter(s -> s.getCode().equals(code)).findFirst();
    }

    public Optional<WorkflowStage> initialStage() {
        return stages.stream().filter(s -> s.getCategory() == StageCategory.DRAFT).findFirst();
    }
}
