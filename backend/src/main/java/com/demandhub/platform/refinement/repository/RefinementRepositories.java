package com.demandhub.platform.refinement.repository;

import com.demandhub.platform.refinement.domain.Decision;
import com.demandhub.platform.refinement.domain.Meeting;
import com.demandhub.platform.refinement.domain.RefinementEntities.ItemStatus;
import com.demandhub.platform.refinement.domain.RefinementEntities.ItemType;
import com.demandhub.platform.refinement.domain.RefinementItem;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public final class RefinementRepositories {

    private RefinementRepositories() {}

    public interface MeetingRepository extends JpaRepository<Meeting, UUID> {
        List<Meeting> findByDemandIdOrderByHeldAtDesc(UUID demandId);
    }

    public interface DecisionRepository extends JpaRepository<Decision, UUID> {
        List<Decision> findByDemandIdOrderByCreatedAtAsc(UUID demandId);
    }

    public interface RefinementItemRepository extends JpaRepository<RefinementItem, UUID> {
        List<RefinementItem> findByDemandIdOrderByCreatedAtAsc(UUID demandId);

        long countByDemandIdAndType(UUID demandId, ItemType type);

        long countByDemandIdAndTypeInAndStatus(UUID demandId, Collection<ItemType> types, ItemStatus status);
    }
}
