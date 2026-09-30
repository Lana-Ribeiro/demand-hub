package com.demandhub.platform.workflow.repository;

import com.demandhub.platform.workflow.domain.WorkflowDefinition;
import com.demandhub.platform.workflow.domain.WorkflowStage;
import com.demandhub.platform.workflow.domain.WorkflowTransition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public final class WorkflowRepositories {

    private WorkflowRepositories() {}

    public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, Long> {
        Optional<WorkflowDefinition> findByCode(String code);
    }

    public interface WorkflowStageRepository extends JpaRepository<WorkflowStage, Long> {
        List<WorkflowStage> findByWorkflowIdOrderByPositionAsc(Long workflowId);

        Optional<WorkflowStage> findByWorkflowIdAndCode(Long workflowId, String code);

        @Query("select distinct s.code from WorkflowStage s")
        List<String> findDistinctCodes();
    }

    public interface WorkflowTransitionRepository extends JpaRepository<WorkflowTransition, Long> {
        @Query("select t from WorkflowTransition t join fetch t.toStage where t.fromStage.id = :stageId")
        List<WorkflowTransition> findFromStage(@Param("stageId") Long stageId);

        List<WorkflowTransition> findByWorkflowId(Long workflowId);
    }
}
