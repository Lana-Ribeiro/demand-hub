package com.demandhub.platform.demand.repository;

import com.demandhub.platform.demand.domain.Comment;
import com.demandhub.platform.demand.domain.CommentVisibility;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.domain.DemandStageHistory;
import com.demandhub.platform.demand.domain.InformationRequest;
import com.demandhub.platform.demand.domain.InformationRequestStatus;
import com.demandhub.platform.demand.domain.LifecycleState;
import com.demandhub.platform.workflow.domain.StageCategory;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public final class DemandRepositories {

    private DemandRepositories() {}

    public interface DemandRepository extends JpaRepository<Demand, UUID>, JpaSpecificationExecutor<Demand> {

        @Query(value = "SELECT nextval('demand_protocol_seq')", nativeQuery = true)
        long nextProtocolNumber();

        @Query("select d from Demand d where d.requester.id = :userId order by d.updatedAt desc")
        List<Demand> findByRequester(@Param("userId") UUID userId);

        @Query("select d from Demand d where d.lifecycleState <> :excluded and d.id <> :exclude")
        List<Demand> findCandidatesForDuplicate(@Param("excluded") LifecycleState excluded, @Param("exclude") UUID exclude);

        @Query("select d from Demand d where d.lifecycleState in :states")
        List<Demand> findByLifecycleStates(@Param("states") Collection<LifecycleState> states);

        Optional<Demand> findByProtocol(String protocol);

        Optional<Demand> findByOriginalId(String originalId);
    }

    public interface DemandStageHistoryRepository extends JpaRepository<DemandStageHistory, UUID> {
        List<DemandStageHistory> findByDemandIdOrderByEnteredAtAsc(UUID demandId);

        Optional<DemandStageHistory> findFirstByDemandIdAndExitedAtIsNullOrderByEnteredAtDesc(UUID demandId);

        @Query("select h from DemandStageHistory h where h.category = :category and h.exitedAt is not null")
        List<DemandStageHistory> findClosedByCategory(@Param("category") StageCategory category);

        @Query("select h from DemandStageHistory h where h.enteredAt >= :from")
        List<DemandStageHistory> findEnteredSince(@Param("from") Instant from);
    }

    public interface InformationRequestRepository extends JpaRepository<InformationRequest, UUID> {
        List<InformationRequest> findByDemandIdOrderByRequestedAtAsc(UUID demandId);

        long countByDemandIdAndStatus(UUID demandId, InformationRequestStatus status);
    }

    public interface CommentRepository extends JpaRepository<Comment, UUID> {
        List<Comment> findByDemandIdOrderByCreatedAtAsc(UUID demandId);

        List<Comment> findByDemandIdAndVisibilityOrderByCreatedAtAsc(UUID demandId, CommentVisibility visibility);

        boolean existsByDemandIdAndExternalId(UUID demandId, String externalId);
    }
}
