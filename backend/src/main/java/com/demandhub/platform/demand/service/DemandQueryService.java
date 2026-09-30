package com.demandhub.platform.demand.service;

import com.demandhub.platform.approval.domain.Approval;
import com.demandhub.platform.approval.repository.ApprovalRepositories.ApprovalRepository;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.domain.ImpactLevel;
import com.demandhub.platform.demand.domain.LifecycleState;
import com.demandhub.platform.demand.repository.DemandRepositories.DemandRepository;
import com.demandhub.platform.demand.repository.DemandRepositories.DemandStageHistoryRepository;
import com.demandhub.platform.demand.web.DemandDtos.BoardColumn;
import com.demandhub.platform.demand.web.DemandDtos.DemandDetail;
import com.demandhub.platform.demand.web.DemandDtos.DemandSummary;
import com.demandhub.platform.demand.web.DemandDtos.LegacyInfo;
import com.demandhub.platform.demand.web.DemandDtos.LinkRef;
import com.demandhub.platform.demand.web.DemandDtos.PriorityRef;
import com.demandhub.platform.demand.web.DemandDtos.StageHistoryItem;
import com.demandhub.platform.demand.web.DemandDtos.StageRef;
import com.demandhub.platform.demand.web.DemandDtos.TypeRef;
import com.demandhub.platform.execution.domain.TechnicalExecution;
import com.demandhub.platform.execution.repository.ExecutionRepositories.TechnicalExecutionRepository;
import com.demandhub.platform.identity.web.UserDtos.UserRef;
import com.demandhub.platform.integration.common.ExternalLink;
import com.demandhub.platform.integration.common.IntegrationRepositories.ExternalLinkRepository;
import com.demandhub.platform.project.web.ProjectDtos.ProjectRef;
import com.demandhub.platform.shared.security.CurrentUser;
import com.demandhub.platform.shared.security.Permissions;
import com.demandhub.platform.workflow.domain.StageCategory;
import com.demandhub.platform.workflow.service.WorkflowEngine;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Modelo de leitura: listas, tabela com filtros, Kanban e detalhe (sem regras de escrita). */
@Service
public class DemandQueryService {

    private final DemandRepository demands;
    private final DemandStageHistoryRepository history;
    private final ExternalLinkRepository links;
    private final TechnicalExecutionRepository executions;
    private final ApprovalRepository approvals;
    private final DemandFieldAccessor accessor;
    private final DemandSubmissionValidator validator;
    private final DemandAccessPolicy access;
    private final WorkflowEngine engine;

    public DemandQueryService(DemandRepository demands, DemandStageHistoryRepository history, ExternalLinkRepository links,
                              TechnicalExecutionRepository executions, ApprovalRepository approvals, DemandFieldAccessor accessor,
                              DemandSubmissionValidator validator, DemandAccessPolicy access, WorkflowEngine engine) {
        this.demands = demands;
        this.history = history;
        this.links = links;
        this.executions = executions;
        this.approvals = approvals;
        this.accessor = accessor;
        this.validator = validator;
        this.access = access;
        this.engine = engine;
    }

    public record Filters(String q, Long projectId, String typeCode, String priorityCode, String stageCode, StageCategory category,
                          LifecycleState lifecycle, UUID requesterId, UUID ownerId, ImpactLevel impactLevel,
                          Instant from, Instant to, Boolean pendingApproval, boolean includeLegacy, boolean includeDrafts) {}

    @Transactional(readOnly = true)
    public Page<DemandSummary> search(Filters f, CurrentUser user, int page, int size, String sort) {
        Specification<Demand> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            var stage = root.join("currentStage", JoinType.LEFT);
            if (!user.has(Permissions.DEMAND_VIEW_ALL)) {
                p.add(cb.equal(root.get("requester").get("id"), user.id()));
            }
            if (!f.includeDrafts()) {
                p.add(cb.notEqual(root.get("lifecycleState"), LifecycleState.DRAFT));
            }
            if (!f.includeLegacy()) {
                p.add(cb.notEqual(root.get("lifecycleState"), LifecycleState.LEGACY));
            }
            p.add(cb.or(cb.notEqual(root.get("lifecycleState"), LifecycleState.CANCELLED), cb.isNotNull(root.get("protocol"))));
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().toLowerCase().trim() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("title")), like), cb.like(cb.lower(root.get("protocol")), like),
                        cb.like(cb.lower(root.get("originalId")), like)));
            }
            if (f.projectId() != null) p.add(cb.equal(root.get("project").get("id"), f.projectId()));
            if (f.typeCode() != null) p.add(cb.equal(root.get("demandType").get("code"), f.typeCode()));
            if (f.priorityCode() != null) p.add(cb.equal(root.get("priority").get("code"), f.priorityCode()));
            if (f.stageCode() != null) p.add(cb.equal(stage.get("code"), f.stageCode()));
            if (f.category() != null) p.add(cb.equal(stage.get("category"), f.category()));
            if (f.lifecycle() != null) p.add(cb.equal(root.get("lifecycleState"), f.lifecycle()));
            if (f.requesterId() != null) p.add(cb.equal(root.get("requester").get("id"), f.requesterId()));
            if (f.ownerId() != null) p.add(cb.equal(root.get("owner").get("id"), f.ownerId()));
            if (f.impactLevel() != null) p.add(cb.equal(root.get("impactLevel"), f.impactLevel()));
            if (f.from() != null) p.add(cb.greaterThanOrEqualTo(root.get("submittedAt"), f.from()));
            if (f.to() != null) p.add(cb.lessThanOrEqualTo(root.get("submittedAt"), f.to()));
            if (Boolean.TRUE.equals(f.pendingApproval())) {
                var sub = q.subquery(UUID.class);
                var a = sub.from(Approval.class);
                sub.select(a.get("demandId")).where(cb.equal(a.get("status"), Approval.Status.PENDING));
                p.add(root.get("id").in(sub));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Sort s = switch (sort == null ? "" : sort) {
            case "priority" -> Sort.by(Sort.Order.asc("priority.rankOrder"), Sort.Order.desc("submittedAt"));
            case "submittedAt" -> Sort.by(Sort.Direction.DESC, "submittedAt");
            case "stageEnteredAt" -> Sort.by(Sort.Direction.ASC, "stageEnteredAt");
            default -> Sort.by(Sort.Direction.DESC, "updatedAt");
        };
        Page<Demand> result = demands.findAll(spec, PageRequest.of(page, Math.min(size, 200), s));
        return new PageImpl<>(summaries(result.getContent()), result.getPageable(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<DemandSummary> mine(CurrentUser user) {
        return summaries(demands.findByRequester(user.id()).stream()
                .filter(d -> !(d.getLifecycleState() == LifecycleState.CANCELLED && d.getProtocol() == null))
                .toList());
    }

    /** Kanban por categoria de estágio (visual; ações acontecem no detalhe, sempre pelo workflow). */
    @Transactional(readOnly = true)
    public List<BoardColumn> board(Filters f, CurrentUser user) {
        List<DemandSummary> items = search(new Filters(f.q(), f.projectId(), f.typeCode(), f.priorityCode(), null, null, null,
                f.requesterId(), f.ownerId(), f.impactLevel(), f.from(), f.to(), f.pendingApproval(), false, false), user, 0, 200, "priority")
                .getContent();
        Map<StageCategory, List<DemandSummary>> byCategory = new EnumMap<>(StageCategory.class);
        items.stream().filter(i -> i.stage() != null).forEach(i -> byCategory.computeIfAbsent(i.stage().category(), k -> new ArrayList<>()).add(i));
        List<BoardColumn> columns = new ArrayList<>();
        for (StageCategory c : List.of(StageCategory.INTAKE, StageCategory.TRIAGE, StageCategory.ON_HOLD, StageCategory.APPROVAL,
                StageCategory.ANALYSIS, StageCategory.REFINEMENT, StageCategory.ARCHITECTURE, StageCategory.READY,
                StageCategory.EXECUTION, StageCategory.DOCUMENTATION, StageCategory.DONE, StageCategory.REJECTED)) {
            columns.add(new BoardColumn(c, label(c), byCategory.getOrDefault(c, List.of())));
        }
        return columns;
    }

    @Transactional(readOnly = true)
    public DemandDetail detail(Demand d, CurrentUser user) {
        access.assertCanView(d, user);
        DemandSummary summary = summaries(List.of(d)).get(0);
        List<StageHistoryItem> stageHistory = history.findByDemandIdOrderByEnteredAtAsc(d.getId()).stream()
                .map(h -> new StageHistoryItem(h.getStageCode(), h.getStageName(), h.getCategory(), h.getAction(), h.getActorName(),
                        h.getReason(), h.getEnteredAt(), h.getExitedAt())).toList();
        List<LinkRef> linkRefs = links.findByDemandId(d.getId()).stream().map(DemandQueryService::linkRef).toList();
        return new DemandDetail(summary, accessor.readAll(d),
                d.getWorkflow() == null ? null : d.getWorkflow().getCode(), d.getWorkflow() == null ? null : d.getWorkflow().getName(),
                engine.available(d, user), d.isDraft() ? validator.validate(d) : Map.of(), stageHistory, linkRefs,
                d.getCreatedAt(), d.getVersion());
    }

    public List<DemandSummary> summaries(List<Demand> list) {
        if (list.isEmpty()) return List.of();
        List<UUID> ids = list.stream().map(Demand::getId).toList();
        Map<UUID, ExternalLink> jira = links.findByDemandIdIn(ids).stream().filter(l -> l.getSystem() == ExternalLink.System.JIRA)
                .collect(Collectors.toMap(ExternalLink::getDemandId, Function.identity(), (a, b) -> a));
        Map<UUID, String> execStatus = executions.findByDemandIdIn(ids).stream()
                .collect(Collectors.toMap(TechnicalExecution::getDemandId, TechnicalExecution::getStatusCode, (a, b) -> a));
        return list.stream().map(d -> new DemandSummary(d.getId(), d.getProtocol(), d.getTitle(), d.getSource(), d.getLifecycleState(),
                d.isReadOnly(), ProjectRef.from(d.getProject()),
                d.getDemandType() == null ? null : new TypeRef(d.getDemandType().getCode(), d.getDemandType().getName(), d.getDemandType().isTechnical()),
                d.getPriority() == null ? null : new PriorityRef(d.getPriority().getCode(), d.getPriority().getName(), d.getPriority().getColor()),
                d.getCurrentStage() == null ? null : new StageRef(d.getCurrentStage().getCode(), d.getCurrentStage().getName(), d.getCurrentStage().getCategory()),
                UserRef.from(d.getRequester()), UserRef.from(d.getOwner()), d.getImpactLevel(), d.getUrgency(), d.getDesiredDate(),
                d.getSubmittedAt(), d.getUpdatedAt(), d.getStageEnteredAt(),
                jira.containsKey(d.getId()) ? linkRef(jira.get(d.getId())) : null, execStatus.get(d.getId()),
                (int) approvals.findByDemandIdAndStatus(d.getId(), Approval.Status.PENDING).size(),
                d.getOriginalId() == null && d.getOriginalStatus() == null ? null
                        : new LegacyInfo(d.getOriginalId(), d.getOriginalCreatedAt(), d.getOriginalStatus(), d.getOriginalOwner())))
                .toList();
    }

    static LinkRef linkRef(ExternalLink l) {
        return new LinkRef(l.getSystem().name(), l.getExternalKey(), l.getUrl(), l.getMode().name(),
                l.getSyncStatus().name(), l.getLastError());
    }

    static String label(StageCategory c) {
        return switch (c) {
            case INTAKE -> "Análise da IA";
            case TRIAGE -> "Triagem PMO";
            case ON_HOLD -> "Aguardando informação";
            case APPROVAL -> "Aprovações";
            case ANALYSIS -> "Análise de capacidade";
            case REFINEMENT -> "Refinamento";
            case ARCHITECTURE -> "Arquitetura";
            case READY -> "Pronta p/ desenvolvimento";
            case EXECUTION -> "Em execução";
            case DOCUMENTATION -> "Documentação";
            case DONE -> "Concluídas";
            case REJECTED -> "Rejeitadas";
            case CANCELLED -> "Canceladas";
            case DRAFT -> "Rascunhos";
        };
    }
}
