package com.demandhub.platform.demand.web;

import com.demandhub.platform.demand.domain.CommentVisibility;
import com.demandhub.platform.demand.domain.DemandSource;
import com.demandhub.platform.demand.domain.ImpactLevel;
import com.demandhub.platform.demand.domain.LifecycleState;
import com.demandhub.platform.demand.domain.Urgency;
import com.demandhub.platform.identity.web.UserDtos.UserRef;
import com.demandhub.platform.project.web.ProjectDtos.ProjectRef;
import com.demandhub.platform.workflow.domain.StageCategory;
import com.demandhub.platform.workflow.service.WorkflowEngine.AvailableTransition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DemandDtos {

    private DemandDtos() {}

    public record TypeRef(String code, String name, boolean technical) {}

    public record PriorityRef(String code, String name, String color) {}

    public record StageRef(String code, String name, StageCategory category) {}

    public record LinkRef(String system, String key, String url, String mode, String syncStatus, String lastError) {}

    public record LegacyInfo(String originalId, Instant originalCreatedAt, String originalStatus, String originalOwner) {}

    public record DemandSummary(UUID id, String protocol, String title, DemandSource source, LifecycleState lifecycleState,
                                boolean readOnly, ProjectRef project, TypeRef type, PriorityRef priority, StageRef stage,
                                UserRef requester, UserRef owner, ImpactLevel impactLevel, Urgency urgency, LocalDate desiredDate,
                                Instant submittedAt, Instant updatedAt, Instant stageEnteredAt, LinkRef jira, String executionStatus,
                                int pendingApprovals, LegacyInfo legacy) {}

    public record StageHistoryItem(String stageCode, String stageName, StageCategory category, String action, String actorName,
                                   String reason, Instant enteredAt, Instant exitedAt) {}

    public record DemandDetail(DemandSummary summary, Map<String, String> fields, String workflowCode, String workflowName,
                               List<AvailableTransition> availableTransitions, Map<String, String> submissionErrors,
                               List<StageHistoryItem> stageHistory, List<LinkRef> links, Instant createdAt, long version) {}

    public record FieldsRequest(@NotNull Map<String, String> fields, DemandSource source) {}

    public record StaffUpdateRequest(@NotNull Map<String, String> fields, @Size(max = 2000) String reason) {}

    public record OwnerRequest(UUID ownerId) {}

    public record TransitionRequest(@NotBlank @Size(max = 60) String action, @Size(max = 2000) String reason) {}

    public record ReasonRequest(@Size(max = 2000) String reason) {}

    public record InformationRequestBody(@NotBlank @Size(max = 4000) String question) {}

    public record AnswerBody(@NotBlank @Size(max = 8000) String response) {}

    public record CommentBody(@NotBlank @Size(max = 8000) String body, CommentVisibility visibility) {}

    public record InformationRequestResponse(UUID id, String question, UserRef requestedBy, Instant requestedAt, String response,
                                             UserRef respondedBy, Instant respondedAt, String status) {}

    public record BoardColumn(StageCategory category, String label, List<DemandSummary> items) {}
}
