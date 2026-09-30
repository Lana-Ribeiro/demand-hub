package com.demandhub.platform.notification.repository;

import com.demandhub.platform.notification.domain.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findTop50ByRecipientIdAndChannelOrderByCreatedAtDesc(UUID recipientId, Notification.Channel channel);

    long countByRecipientIdAndChannelAndReadAtIsNull(UUID recipientId, Notification.Channel channel);

    List<Notification> findByDemandIdOrderByCreatedAtDesc(UUID demandId);
}
