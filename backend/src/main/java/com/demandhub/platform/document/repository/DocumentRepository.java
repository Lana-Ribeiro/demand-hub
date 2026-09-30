package com.demandhub.platform.document.repository;

import com.demandhub.platform.document.domain.Document;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByDemandIdOrderByUploadedAtAsc(UUID demandId);

    List<Document> findByDemandIdAndKindOrderByUploadedAtDesc(UUID demandId, Document.Kind kind);

    Optional<Document> findFirstByDemandIdAndKindOrderByUploadedAtDesc(UUID demandId, Document.Kind kind);

    boolean existsByDemandIdAndKind(UUID demandId, Document.Kind kind);
}
