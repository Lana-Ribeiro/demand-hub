package com.demandhub.platform.demand.web;

import com.demandhub.platform.approval.domain.Approval;
import com.demandhub.platform.approval.service.ApprovalService;
import com.demandhub.platform.audit.domain.AuditLog;
import com.demandhub.platform.audit.repository.AuditLogRepository;
import com.demandhub.platform.demand.domain.Comment;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.domain.ImpactLevel;
import com.demandhub.platform.demand.domain.InformationRequest;
import com.demandhub.platform.demand.domain.LifecycleState;
import com.demandhub.platform.demand.service.DemandQueryService;
import com.demandhub.platform.demand.service.DemandQueryService.Filters;
import com.demandhub.platform.demand.service.DemandService;
import com.demandhub.platform.demand.web.DemandDtos.AnswerBody;
import com.demandhub.platform.demand.web.DemandDtos.BoardColumn;
import com.demandhub.platform.demand.web.DemandDtos.CommentBody;
import com.demandhub.platform.demand.web.DemandDtos.DemandDetail;
import com.demandhub.platform.demand.web.DemandDtos.DemandSummary;
import com.demandhub.platform.demand.web.DemandDtos.FieldsRequest;
import com.demandhub.platform.demand.web.DemandDtos.InformationRequestBody;
import com.demandhub.platform.demand.web.DemandDtos.InformationRequestResponse;
import com.demandhub.platform.demand.web.DemandDtos.OwnerRequest;
import com.demandhub.platform.demand.web.DemandDtos.ReasonRequest;
import com.demandhub.platform.demand.web.DemandDtos.StaffUpdateRequest;
import com.demandhub.platform.demand.web.DemandDtos.TransitionRequest;
import com.demandhub.platform.identity.web.UserDtos.UserRef;
import com.demandhub.platform.shared.error.ApiException;
import com.demandhub.platform.shared.security.CurrentUser;
import com.demandhub.platform.shared.security.Permissions;
import com.demandhub.platform.shared.security.SecurityUtils;
import com.demandhub.platform.workflow.domain.StageCategory;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demands")
public class DemandController {

    private final DemandService service;
    private final DemandQueryService query;
    private final ApprovalService approvals;
    private final AuditLogRepository auditLogs;

    public DemandController(DemandService service, DemandQueryService query, ApprovalService approvals, AuditLogRepository auditLogs) {
        this.service = service;
        this.query = query;
        this.approvals = approvals;
        this.auditLogs = auditLogs;
    }

    // ------------------------------------------------------------- consultas

    @GetMapping
    public Page<DemandSummary> search(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) Long projectId,
                                      @RequestParam(required = false) String type,
                                      @RequestParam(required = false) String priority,
                                      @RequestParam(required = false) String stage,
                                      @RequestParam(required = false) StageCategory category,
                                      @RequestParam(required = false) LifecycleState lifecycle,
                                      @RequestParam(required = false) UUID requesterId,
                                      @RequestParam(required = false) UUID ownerId,
                                      @RequestParam(required = false) ImpactLevel impact,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                      @RequestParam(required = false) Boolean pendingApproval,
                                      @RequestParam(defaultValue = "false") boolean includeLegacy,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "25") int size,
                                      @RequestParam(required = false) String sort) {
        Filters f = new Filters(q, projectId, type, priority, stage, category, lifecycle, requesterId, ownerId, impact, from, to,
                pendingApproval, includeLegacy, false);
        return query.search(f, SecurityUtils.currentUser(), page, size, sort);
    }

    @GetMapping("/mine")
    public List<DemandSummary> mine() {
        return query.mine(SecurityUtils.currentUser());
    }

    @GetMapping("/board")
    @PreAuthorize("hasAuthority('DEMAND_VIEW_ALL')")
    public List<BoardColumn> board(@RequestParam(required = false) String q,
                                   @RequestParam(required = false) Long projectId,
                                   @RequestParam(required = false) String type,
                                   @RequestParam(required = false) String priority,
                                   @RequestParam(required = false) UUID ownerId) {
        return query.board(new Filters(q, projectId, type, priority, null, null, null, null, ownerId, null, null, null, null, false, false),
                SecurityUtils.currentUser());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public DemandDetail get(@PathVariable UUID id) {
        CurrentUser user = SecurityUtils.currentUser();
        return query.detail(service.getForView(id, user), user);
    }

    // ------------------------------------------------------------- rascunho e envio

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DEMAND_CREATE')")
    @Transactional
    public DemandDetail create(@Valid @RequestBody FieldsRequest req) {
        CurrentUser user = SecurityUtils.currentUser();
        return query.detail(service.createDraft(req.fields(), req.source(), user), user);
    }

    @PutMapping("/{id}")
    @Transactional
    public DemandDetail updateDraft(@PathVariable UUID id, @Valid @RequestBody FieldsRequest req) {
        CurrentUser user = SecurityUtils.currentUser();
        return query.detail(service.updateDraft(id, req.fields(), user), user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discard(@PathVariable UUID id) {
        service.discardDraft(id, SecurityUtils.currentUser());
    }

    @GetMapping("/{id}/validation")
    public Map<String, Object> validation(@PathVariable UUID id) {
        Map<String, String> errors = service.validateForSubmission(id, SecurityUtils.currentUser());
        return Map.of("ready", errors.isEmpty(), "fieldErrors", errors);
    }

    @PostMapping("/{id}/submit")
    @Transactional
    public DemandDetail submit(@PathVariable UUID id) {
        CurrentUser user = SecurityUtils.currentUser();
        return query.detail(service.submit(id, user), user);
    }

    // ------------------------------------------------------------- PMO / workflow

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('DEMAND_EDIT_ALL','DEMAND_TRIAGE')")
    @Transactional
    public DemandDetail staffUpdate(@PathVariable UUID id, @Valid @RequestBody StaffUpdateRequest req) {
        CurrentUser user = SecurityUtils.currentUser();
        return query.detail(service.updateByStaff(id, req.fields(), req.reason(), user), user);
    }

    @PutMapping("/{id}/owner")
    @PreAuthorize("hasAuthority('DEMAND_TRIAGE')")
    @Transactional
    public DemandDetail owner(@PathVariable UUID id, @RequestBody OwnerRequest req) {
        CurrentUser user = SecurityUtils.currentUser();
        return query.detail(service.assignOwner(id, req.ownerId(), user), user);
    }

    @PostMapping("/{id}/transitions")
    @Transactional
    public DemandDetail transition(@PathVariable UUID id, @Valid @RequestBody TransitionRequest req) {
        CurrentUser user = SecurityUtils.currentUser();
        return query.detail(service.transition(id, req.action(), req.reason(), user), user);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('DEMAND_TRIAGE')")
    @Transactional
    public DemandDetail approve(@PathVariable UUID id, @RequestBody(required = false) ReasonRequest req) {
        CurrentUser user = SecurityUtils.currentUser();
        return query.detail(service.transition(id, "APPROVE", req == null ? null : req.reason(), user), user);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('DEMAND_TRIAGE')")
    @Transactional
    public DemandDetail reject(@PathVariable UUID id, @RequestBody ReasonRequest req) {
        CurrentUser user = SecurityUtils.currentUser();
        return query.detail(service.transition(id, "REJECT", req.reason(), user), user);
    }

    @PostMapping("/{id}/request-information")
    @PreAuthorize("hasAuthority('DEMAND_TRIAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public InformationRequestResponse requestInformation(@PathVariable UUID id, @Valid @RequestBody InformationRequestBody req) {
        return toResponse(service.requestInformation(id, req.question(), SecurityUtils.currentUser()));
    }

    @GetMapping("/{id}/information-requests")
    @Transactional(readOnly = true)
    public List<InformationRequestResponse> informationRequests(@PathVariable UUID id) {
        return service.informationRequests(id, SecurityUtils.currentUser()).stream().map(DemandController::toResponse).toList();
    }

    @PostMapping("/{id}/information-requests/{requestId}/answer")
    @Transactional
    public InformationRequestResponse answer(@PathVariable UUID id, @PathVariable UUID requestId, @Valid @RequestBody AnswerBody req) {
        return toResponse(service.answerInformation(id, requestId, req.response(), SecurityUtils.currentUser()));
    }

    @GetMapping("/{id}/approvals")
    @Transactional(readOnly = true)
    public List<ApprovalView> approvals(@PathVariable UUID id) {
        service.getForView(id, SecurityUtils.currentUser());
        return approvals.forDemand(id).stream().map(ApprovalView::from).toList();
    }

    // ------------------------------------------------------------- comentários e histórico

    @GetMapping("/{id}/comments")
    public List<Comment> comments(@PathVariable UUID id) {
        return service.comments(id, SecurityUtils.currentUser());
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Comment addComment(@PathVariable UUID id, @Valid @RequestBody CommentBody req) {
        return service.addComment(id, req.body(), req.visibility(), SecurityUtils.currentUser());
    }

    /** Trilha de auditoria da demanda — restrita a usuários internos. */
    @GetMapping("/{id}/audit")
    @PreAuthorize("hasAnyAuthority('AUDIT_VIEW','DEMAND_VIEW_ALL')")
    public List<AuditLog> audit(@PathVariable UUID id) {
        Demand d = service.getForView(id, SecurityUtils.currentUser());
        if (!SecurityUtils.currentUser().has(Permissions.DEMAND_VIEW_ALL)) {
            throw ApiException.forbidden("Acesso restrito.");
        }
        return auditLogs.findByDemandIdOrderByCreatedAtDesc(d.getId());
    }

    static InformationRequestResponse toResponse(InformationRequest r) {
        return new InformationRequestResponse(r.getId(), r.getQuestion(), UserRef.from(r.getRequestedBy()), r.getRequestedAt(),
                r.getResponse(), UserRef.from(r.getRespondedBy()), r.getRespondedAt(), r.getStatus().name());
    }

    public record ApprovalView(UUID id, UUID demandId, String ruleName, String stageCode, String approverRole, String status,
                               UserRef decidedBy, Instant decidedAt, String comment, Instant createdAt) {
        public static ApprovalView from(Approval a) {
            return new ApprovalView(a.getId(), a.getDemandId(), a.getRuleName(), a.getStageCode(), a.getApproverRole(),
                    a.getStatus().name(), UserRef.from(a.getDecidedBy()), a.getDecidedAt(), a.getComment(), a.getCreatedAt());
        }
    }
}
