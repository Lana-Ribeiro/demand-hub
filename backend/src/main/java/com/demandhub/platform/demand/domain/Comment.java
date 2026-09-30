package com.demandhub.platform.demand.domain;

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
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID demandId;
    private UUID authorId;
    private String authorName;
    private String body;

    @Enumerated(EnumType.STRING)
    private CommentVisibility visibility = CommentVisibility.PUBLIC;

    @Enumerated(EnumType.STRING)
    private CommentSource source = CommentSource.PLATFORM;

    private String externalId;
    private Instant createdAt;
}
