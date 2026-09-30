package com.demandhub.platform.integration.common;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public final class IntegrationRepositories {

    private IntegrationRepositories() {}

    public interface ExternalLinkRepository extends JpaRepository<ExternalLink, UUID> {
        Optional<ExternalLink> findByDemandIdAndSystem(UUID demandId, ExternalLink.System system);

        Optional<ExternalLink> findBySystemAndExternalKey(ExternalLink.System system, String externalKey);

        Optional<ExternalLink> findBySystemAndProjectRefAndExternalKey(ExternalLink.System system, String projectRef, String externalKey);

        List<ExternalLink> findByDemandIdIn(Collection<UUID> demandIds);

        List<ExternalLink> findByDemandId(UUID demandId);
    }

    public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
        List<WebhookEvent> findTop100ByOrderByReceivedAtDesc();
    }
}
