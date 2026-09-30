package com.demandhub.platform.notification.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    public enum Channel { IN_APP, EMAIL, TEAMS }

    public enum Status { SENT, FAILED, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    private Channel channel;

    private String eventType;
    private String subject;
    private String body;
    private UUID demandId;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String error;
    private Instant createdAt;
    private Instant readAt;
}
