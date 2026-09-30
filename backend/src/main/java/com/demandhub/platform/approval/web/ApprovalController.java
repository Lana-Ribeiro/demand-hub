package com.demandhub.platform.approval.web;

import com.demandhub.platform.approval.domain.Approval;
import com.demandhub.platform.approval.service.ApprovalService;
import com.demandhub.platform.demand.repository.DemandRepositories.DemandRepository;
import com.demandhub.platform.demand.service.DemandQueryService;
import com.demandhub.platform.demand.web.DemandController.ApprovalView;
import com.demandhub.platform.demand.web.DemandDtos.DemandSummary;
import com.demandhub.platform.shared.security.SecurityUtils;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approvals")
@PreAuthorize("hasAuthority('APPROVAL_DECIDE')")
public class ApprovalController {

    private final ApprovalService service;
    private final DemandRepository demands;
    private final DemandQueryService query;

    public ApprovalController(ApprovalService service, DemandRepository demands, DemandQueryService query) {
        this.service = service;
        this.demands = demands;
        this.query = query;
    }

    /** Caixa de aprovações pendentes dos papéis do usuário. */
    @GetMapping("/pending")
    @Transactional(readOnly = true)
    public List<PendingApproval> pending() {
        List<Approval> pending = service.pendingFor(SecurityUtils.currentUser());
        Map<UUID, DemandSummary> summaries = query.summaries(demands.findAllById(pending.stream().map(Approval::getDemandId).distinct().toList()))
                .stream().collect(Collectors.toMap(DemandSummary::id, Function.identity()));
        return pending.stream().map(a -> new PendingApproval(ApprovalView.from(a), summaries.get(a.getDemandId()))).toList();
    }

    @PostMapping("/{id}/decision")
    @Transactional
    public ApprovalView decide(@PathVariable UUID id, @Validated @RequestBody DecisionRequest req) {
        return ApprovalView.from(service.decide(id, req.approve(), req.comment(), SecurityUtils.currentUser()));
    }

    public record DecisionRequest(@NotNull Boolean approve, @Size(max = 4000) String comment) {}

    public record PendingApproval(ApprovalView approval, DemandSummary demand) {}
}
