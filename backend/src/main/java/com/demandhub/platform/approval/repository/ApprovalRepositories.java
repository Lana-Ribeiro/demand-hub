package com.demandhub.platform.approval.repository;

import com.demandhub.platform.approval.domain.Approval;
import com.demandhub.platform.approval.domain.ApprovalRule;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public final class ApprovalRepositories {

    private ApprovalRepositories() {}

    public interface ApprovalRuleRepository extends JpaRepository<ApprovalRule, Long> {
        List<ApprovalRule> findByWorkflowIdAndStageCodeAndActiveTrueOrderByPositionAsc(Long workflowId, String stageCode);

        List<ApprovalRule> findByWorkflowIdOrderByStageCodeAscPositionAsc(Long workflowId);
    }

    public interface ApprovalRepository extends JpaRepository<Approval, UUID> {
        List<Approval> findByDemandIdOrderByCreatedAtAsc(UUID demandId);

        List<Approval> findByDemandIdAndStageCodeAndStatusIn(UUID demandId, String stageCode, Collection<Approval.Status> statuses);

        List<Approval> findByDemandIdAndStatus(UUID demandId, Approval.Status status);

        List<Approval> findByStatusAndApproverRoleInOrderByCreatedAtAsc(Approval.Status status, Collection<String> roles);

        long countByStatus(Approval.Status status);
    }
}
