package com.demandhub.platform.document.domain;

import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
public class Document {

    public enum Kind { DOCUMENT, PRESENTATION, ATTACHMENT, TECHNICAL_DOC, CLIENT_DOC }

    public enum ExtractionStatus { PENDING, EXTRACTED, FAILED, NOT_APPLICABLE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID demandId;

    @Enumerated(EnumType.STRING)
    private Kind kind;

    private String fileName;
    private String contentType;
    private long sizeBytes;
    private String storageKey;
    private String sha256;

    @Basic(fetch = FetchType.LAZY)
    private String extractedText;

    @Enumerated(EnumType.STRING)
    private ExtractionStatus extractionStatus;

    private String extractionError;
    private UUID uploadedBy;
    private Instant uploadedAt;

    public boolean isGenerated() {
        return kind == Kind.TECHNICAL_DOC || kind == Kind.CLIENT_DOC;
    }
}
