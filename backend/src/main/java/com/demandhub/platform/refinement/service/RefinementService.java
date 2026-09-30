package com.demandhub.platform.refinement.service;

import com.demandhub.platform.audit.service.AuditService;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.service.DemandService;
import com.demandhub.platform.refinement.domain.Decision;
import com.demandhub.platform.refinement.domain.Meeting;
import com.demandhub.platform.refinement.domain.RefinementEntities.DecisionType;
import com.demandhub.platform.refinement.domain.RefinementEntities.ItemOrigin;
import com.demandhub.platform.refinement.domain.RefinementEntities.ItemStatus;
import com.demandhub.platform.refinement.domain.RefinementEntities.ItemType;
import com.demandhub.platform.refinement.domain.RefinementItem;
import com.demandhub.platform.refinement.repository.RefinementRepositories.DecisionRepository;
import com.demandhub.platform.refinement.repository.RefinementRepositories.MeetingRepository;
import com.demandhub.platform.refinement.repository.RefinementRepositories.RefinementItemRepository;
import com.demandhub.platform.shared.error.ApiException;
import com.demandhub.platform.shared.security.CurrentUser;
import com.demandhub.platform.shared.security.Permissions;
import com.demandhub.platform.workflow.service.ExitRequirementChecker;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefinementService implements ExitRequirementChecker {

    private final MeetingRepository meetings;
    private final DecisionRepository decisions;
    private final RefinementItemRepository items;
    private final DemandService demandService;
    private final AuditService audit;
    private final Clock clock;

    public RefinementService(MeetingRepository meetings, DecisionRepository decisions, RefinementItemRepository items,
                             DemandService demandService, AuditService audit, Clock clock) {
        this.meetings = meetings;
        this.decisions = decisions;
        this.items = items;
        this.demandService = demandService;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot(UUID demandId, CurrentUser user) {
        demandService.getForView(demandId, user);
        return new Snapshot(meetings.findByDemandIdOrderByHeldAtDesc(demandId), decisions.findByDemandIdOrderByCreatedAtAsc(demandId),
                items.findByDemandIdOrderByCreatedAtAsc(demandId));
    }

    @Transactional
    public Meeting addMeeting(UUID demandId, String title, Instant heldAt, String participants, String notes, CurrentUser user) {
        assertCanManage(demandId, user, Permissions.REFINEMENT_MANAGE);
        Meeting m = new Meeting();
        m.setDemandId(demandId);
        m.setTitle(title.trim());
        m.setHeldAt(heldAt == null ? clock.instant() : heldAt);
        m.setParticipants(participants);
        m.setNotes(notes);
        m.setCreatedBy(user.id());
        m.setCreatedAt(clock.instant());
        meetings.save(m);
        audit.event("MEETING_RECORDED").entity("Meeting", m.getId()).demand(demandId).metadata(title).record();
        return m;
    }

    @Transactional
    public Decision addDecision(UUID demandId, UUID meetingId, DecisionType type, String description, String rationale,
                                String decidedBy, CurrentUser user) {
        String permission = type == DecisionType.ARCHITECTURAL ? Permissions.ARCHITECTURE_MANAGE : Permissions.REFINEMENT_MANAGE;
        if (!user.has(permission) && !user.has(Permissions.REFINEMENT_MANAGE)) {
            throw ApiException.forbidden("Sem permissão para registrar decisões.");
        }
        assertCanManage(demandId, user, null);
        Decision d = new Decision();
        d.setDemandId(demandId);
        d.setMeetingId(meetingId);
        d.setType(type);
        d.setDescription(description.trim());
        d.setRationale(rationale);
        d.setDecidedBy(decidedBy == null || decidedBy.isBlank() ? user.fullName() : decidedBy);
        d.setCreatedBy(user.id());
        d.setCreatedAt(clock.instant());
        decisions.save(d);
        audit.event("DECISION_RECORDED").entity("Decision", d.getId()).demand(demandId)
                .change("decision", null, type + ": " + description).reason(rationale).record();
        return d;
    }

    @Transactional
    public RefinementItem addItem(UUID demandId, ItemType type, String description, ItemOrigin origin, CurrentUser user) {
        assertCanManage(demandId, user, Permissions.REFINEMENT_MANAGE);
        RefinementItem item = new RefinementItem();
        item.setDemandId(demandId);
        item.setType(type);
        item.setDescription(description.trim());
        item.setOrigin(origin);
        item.setCreatedBy(user.id());
        item.setCreatedAt(clock.instant());
        items.save(item);
        audit.event("REFINEMENT_ITEM_ADDED").entity("RefinementItem", item.getId()).demand(demandId)
                .change(type.name(), null, description).metadata("origin=" + origin).record();
        return item;
    }

    @Transactional
    public RefinementItem setItemStatus(UUID demandId, UUID itemId, ItemStatus status, CurrentUser user) {
        assertCanManage(demandId, user, Permissions.REFINEMENT_MANAGE);
        RefinementItem item = items.findById(itemId).filter(i -> i.getDemandId().equals(demandId))
                .orElseThrow(() -> ApiException.notFound("Item de refinamento", itemId));
        ItemStatus before = item.getStatus();
        item.setStatus(status);
        item.setResolvedAt(status == ItemStatus.RESOLVED ? clock.instant() : null);
        audit.event("REFINEMENT_ITEM_STATUS").entity("RefinementItem", itemId).demand(demandId).change("status", before, status).record();
        return item;
    }

    private void assertCanManage(UUID demandId, CurrentUser user, String permission) {
        if (permission != null && !user.has(permission)) {
            throw ApiException.forbidden("Sem permissão para registrar refinamento.");
        }
        Demand d = demandService.getForView(demandId, user);
        if (d.isReadOnly() || d.isDraft()) {
            throw ApiException.businessRule("DEMAND_NOT_EDITABLE", "A demanda não aceita registros de refinamento no estado atual.");
        }
    }

    @Override
    public String code() {
        return "REFINEMENT_COMPLETE";
    }

    @Override
    public Optional<String> unmetReason(Demand demand) {
        List<String> missing = new ArrayList<>();
        if (items.countByDemandIdAndType(demand.getId(), ItemType.FUNCTIONAL_REQUIREMENT) == 0) {
            missing.add("ao menos um requisito funcional");
        }
        if (items.countByDemandIdAndType(demand.getId(), ItemType.ACCEPTANCE_CRITERION) == 0) {
            missing.add("ao menos um critério de aceite");
        }
        long open = items.countByDemandIdAndTypeInAndStatus(demand.getId(), EnumSet.of(ItemType.QUESTION, ItemType.PENDING_ITEM), ItemStatus.OPEN);
        if (open > 0) {
            missing.add("resolver " + open + " dúvida(s)/pendência(s) em aberto");
        }
        return missing.isEmpty() ? Optional.empty() : Optional.of("Refinamento incompleto: " + String.join("; ", missing) + ".");
    }

    public record Snapshot(List<Meeting> meetings, List<Decision> decisions, List<RefinementItem> items) {}
}
