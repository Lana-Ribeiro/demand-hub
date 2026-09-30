package com.demandhub.platform.ai.repository;

import com.demandhub.platform.ai.domain.AiAgentRun;
import com.demandhub.platform.ai.domain.AiAnalysis;
import com.demandhub.platform.ai.domain.AiSuggestion;
import com.demandhub.platform.ai.domain.ChatMessage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public final class AiRepositories {

    private AiRepositories() {}

    public interface AiAgentRunRepository extends JpaRepository<AiAgentRun, UUID> {
        List<AiAgentRun> findByDemandIdOrderByCreatedAtDesc(UUID demandId);
    }

    public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, UUID> {
        List<AiSuggestion> findByDemandIdOrderByCreatedAtDesc(UUID demandId);

        List<AiSuggestion> findByDemandIdAndStatusOrderByCreatedAtDesc(UUID demandId, AiSuggestion.Status status);

        List<AiSuggestion> findByDemandIdAndKindAndStatus(UUID demandId, AiSuggestion.Kind kind, AiSuggestion.Status status);

        List<AiSuggestion> findByDemandIdAndSourceTypeInAndStatus(UUID demandId, Collection<AiSuggestion.SourceType> sources, AiSuggestion.Status status);

        List<AiSuggestion> findByDemandIdAndKindAndSourceTypeAndStatus(UUID demandId, AiSuggestion.Kind kind, AiSuggestion.SourceType sourceType, AiSuggestion.Status status);

        long countByStatus(AiSuggestion.Status status);
    }

    public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, UUID> {
        Optional<AiAnalysis> findFirstByDemandIdAndKindOrderByCreatedAtDesc(UUID demandId, AiAnalysis.Kind kind);

        List<AiAnalysis> findByDemandIdOrderByCreatedAtDesc(UUID demandId);
    }

    public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
        List<ChatMessage> findByDemandIdOrderByCreatedAtAsc(UUID demandId);
    }
}
