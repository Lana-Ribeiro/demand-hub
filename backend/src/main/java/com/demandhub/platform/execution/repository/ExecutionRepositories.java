package com.demandhub.platform.execution.repository;

import com.demandhub.platform.execution.domain.ExecutionEvent;
import com.demandhub.platform.execution.domain.ExecutionStatus;
import com.demandhub.platform.execution.domain.TechnicalExecution;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public final class ExecutionRepositories {

    private ExecutionRepositories() {}

    public interface ExecutionStatusRepository extends JpaRepository<ExecutionStatus, String> {
        List<ExecutionStatus> findAllByOrderByPositionAsc();

        Optional<ExecutionStatus> findByScmLabelIgnoreCase(String label);

        Optional<ExecutionStatus> findFirstByDoneTrueOrderByPositionAsc();

        Optional<ExecutionStatus> findFirstByOrderByPositionAsc();
    }

    public interface TechnicalExecutionRepository extends JpaRepository<TechnicalExecution, UUID> {
        Optional<TechnicalExecution> findByDemandId(UUID demandId);

        Optional<TechnicalExecution> findByExternalLinkId(UUID linkId);

        List<TechnicalExecution> findByDemandIdIn(Collection<UUID> demandIds);

        List<TechnicalExecution> findAllByOrderByLastEventAtDesc();
    }

    public interface ExecutionEventRepository extends JpaRepository<ExecutionEvent, UUID> {
        List<ExecutionEvent> findByExecutionIdOrderByReceivedAtAsc(UUID executionId);
    }
}
