package com.demandhub.platform.document.web;

import com.demandhub.platform.ai.service.DocumentAnalysisService;
import com.demandhub.platform.demand.service.DemandService;
import com.demandhub.platform.document.domain.Document;
import com.demandhub.platform.document.service.DocumentService;
import com.demandhub.platform.shared.error.ApiException;
import com.demandhub.platform.shared.security.CurrentUser;
import com.demandhub.platform.shared.security.Permissions;
import com.demandhub.platform.shared.security.SecurityUtils;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class DocumentController {

    private final DocumentService service;
    private final DocumentAnalysisService analysis;
    private final DemandService demandService;

    public DocumentController(DocumentService service, DocumentAnalysisService analysis, DemandService demandService) {
        this.service = service;
        this.analysis = analysis;
        this.demandService = demandService;
    }

    @PostMapping(value = "/api/demands/{demandId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentView upload(@PathVariable UUID demandId, @RequestParam("file") MultipartFile file,
                               @RequestParam(value = "kind", defaultValue = "DOCUMENT") Document.Kind kind) {
        return DocumentView.from(service.upload(demandId, kind, file, SecurityUtils.currentUser()));
    }

    @GetMapping("/api/demands/{demandId}/documents")
    public List<DocumentView> list(@PathVariable UUID demandId) {
        return service.list(demandId, SecurityUtils.currentUser()).stream().map(DocumentView::from).toList();
    }

    @GetMapping("/api/documents/{documentId}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID documentId) {
        DocumentService.Download d = service.download(documentId, SecurityUtils.currentUser());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(d.contentType()))
                .body(d.content());
    }

    /** Reexecuta a extração por IA de um documento. */
    @PostMapping("/api/documents/{documentId}/analyze")
    public Map<String, Object> analyze(@PathVariable UUID documentId) {
        CurrentUser user = SecurityUtils.currentUser();
        if (!user.has(Permissions.AI_USE)) {
            throw ApiException.forbidden("Sem permissão para usar a IA.");
        }
        Document doc = service.get(documentId);
        demandService.getForView(doc.getDemandId(), user);
        return Map.of("suggestionsCreated", analysis.analyze(documentId));
    }

    public record DocumentView(UUID id, UUID demandId, Document.Kind kind, String fileName, String contentType, long sizeBytes,
                               String sha256, Document.ExtractionStatus extractionStatus, String extractionError, Instant uploadedAt) {
        static DocumentView from(Document d) {
            return new DocumentView(d.getId(), d.getDemandId(), d.getKind(), d.getFileName(), d.getContentType(), d.getSizeBytes(),
                    d.getSha256(), d.getExtractionStatus(), d.getExtractionError(), d.getUploadedAt());
        }
    }
}
