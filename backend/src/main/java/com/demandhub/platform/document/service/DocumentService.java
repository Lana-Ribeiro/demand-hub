package com.demandhub.platform.document.service;

import com.demandhub.platform.audit.service.AuditService;
import com.demandhub.platform.config.AppProperties;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.service.DemandAccessPolicy;
import com.demandhub.platform.demand.service.DemandService;
import com.demandhub.platform.document.domain.Document;
import com.demandhub.platform.document.repository.DocumentRepository;
import com.demandhub.platform.shared.error.ApiException;
import com.demandhub.platform.shared.security.CurrentUser;
import com.demandhub.platform.shared.security.Permissions;
import com.demandhub.platform.workflow.service.ExitRequirementChecker;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService implements ExitRequirementChecker {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    /** Extensão → MIME types aceitos (detectados pelo conteúdo). */
    private static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/x-tika-ooxml")),
            Map.entry("doc", Set.of("application/msword", "application/x-tika-msoffice")),
            Map.entry("pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation", "application/x-tika-ooxml")),
            Map.entry("ppt", Set.of("application/vnd.ms-powerpoint", "application/x-tika-msoffice")),
            Map.entry("xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/x-tika-ooxml")),
            Map.entry("xls", Set.of("application/vnd.ms-excel", "application/x-tika-msoffice")),
            Map.entry("txt", Set.of("text/plain")),
            Map.entry("md", Set.of("text/plain", "text/x-web-markdown", "text/markdown")),
            Map.entry("csv", Set.of("text/plain", "text/csv")),
            Map.entry("png", Set.of("image/png")),
            Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")));

    private static final Set<String> TEXT_EXTRACTABLE = Set.of("pdf", "docx", "doc", "pptx", "ppt", "xlsx", "xls", "txt", "md", "csv");

    private final DocumentRepository documents;
    private final DocumentStorage storage;
    private final TextExtractionService extraction;
    private final DemandService demandService;
    private final DemandAccessPolicy access;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final AppProperties props;
    private final Clock clock;

    public DocumentService(DocumentRepository documents, DocumentStorage storage, TextExtractionService extraction,
                           DemandService demandService, DemandAccessPolicy access, AuditService audit,
                           ApplicationEventPublisher events, AppProperties props, Clock clock) {
        this.documents = documents;
        this.storage = storage;
        this.extraction = extraction;
        this.demandService = demandService;
        this.access = access;
        this.audit = audit;
        this.events = events;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public Document upload(UUID demandId, Document.Kind kind, MultipartFile file, CurrentUser user) {
        if (kind == Document.Kind.TECHNICAL_DOC || kind == Document.Kind.CLIENT_DOC) {
            throw ApiException.badRequest("INVALID_KIND", "Documentação gerada não é enviada por upload.");
        }
        Demand demand = demandService.getForView(demandId, user);
        if (demand.isReadOnly()) {
            throw ApiException.businessRule("DEMAND_READ_ONLY", "Demanda somente leitura.");
        }
        if (!demand.isOwnedBy(user.id()) && !user.has(Permissions.DEMAND_EDIT_ALL)) {
            throw ApiException.forbidden("Sem permissão para anexar arquivos a esta demanda.");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "Arquivo vazio.");
        }
        long max = props.upload().maxSizeMb() * 1024L * 1024L;
        if (file.getSize() > max) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Arquivo excede " + props.upload().maxSizeMb() + " MB.");
        }
        String fileName = sanitize(file.getOriginalFilename());
        String ext = extension(fileName);
        if (!ALLOWED.containsKey(ext)) {
            throw ApiException.badRequest("FILE_TYPE_NOT_ALLOWED", "Tipo de arquivo não permitido: ." + ext);
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception e) {
            throw ApiException.badRequest("UPLOAD_FAILED", "Não foi possível ler o arquivo.");
        }
        String detected = extraction.detectMimeType(content, fileName);
        if (!ALLOWED.get(ext).contains(detected)) {
            throw ApiException.badRequest("FILE_CONTENT_MISMATCH",
                    "O conteúdo do arquivo não corresponde à extensão ." + ext + " (detectado: " + detected + ").");
        }

        Document doc = new Document();
        doc.setDemandId(demandId);
        doc.setKind(kind);
        doc.setFileName(fileName);
        doc.setContentType(detected);
        doc.setSizeBytes(content.length);
        doc.setSha256(sha256(content));
        doc.setStorageKey(storage.store(content));
        doc.setUploadedBy(user.id());
        doc.setUploadedAt(clock.instant());
        if (TEXT_EXTRACTABLE.contains(ext)) {
            try {
                doc.setExtractedText(extraction.extract(content));
                doc.setExtractionStatus(Document.ExtractionStatus.EXTRACTED);
            } catch (Exception e) {
                log.warn("Falha na extração de texto do documento {}: {}", fileName, e.getClass().getSimpleName());
                doc.setExtractionStatus(Document.ExtractionStatus.FAILED);
                doc.setExtractionError("Não foi possível extrair o texto do arquivo.");
            }
        } else {
            doc.setExtractionStatus(Document.ExtractionStatus.NOT_APPLICABLE);
        }
        documents.save(doc);
        audit.event("DOCUMENT_UPLOADED").entity("Document", doc.getId()).demand(demandId)
                .metadata("kind=" + kind + "; file=" + fileName + "; size=" + content.length).record();
        events.publishEvent(new DocumentUploaded(demandId, doc.getId(), kind, doc.getExtractionStatus() == Document.ExtractionStatus.EXTRACTED));
        return doc;
    }

    /** Persiste documentação gerada (Markdown) — técnica ou para o cliente. */
    @Transactional
    public Document saveGenerated(UUID demandId, Document.Kind kind, String fileName, String markdown, UUID userId) {
        byte[] content = markdown.getBytes(StandardCharsets.UTF_8);
        Document doc = new Document();
        doc.setDemandId(demandId);
        doc.setKind(kind);
        doc.setFileName(fileName);
        doc.setContentType("text/markdown");
        doc.setSizeBytes(content.length);
        doc.setSha256(sha256(content));
        doc.setStorageKey(storage.store(content));
        doc.setExtractedText(markdown);
        doc.setExtractionStatus(Document.ExtractionStatus.EXTRACTED);
        doc.setUploadedBy(userId);
        doc.setUploadedAt(clock.instant());
        documents.save(doc);
        audit.event("DOCUMENTATION_GENERATED").entity("Document", doc.getId()).demand(demandId).metadata("kind=" + kind).record();
        return doc;
    }

    @Transactional(readOnly = true)
    public List<Document> list(UUID demandId, CurrentUser user) {
        demandService.getForView(demandId, user);
        return documents.findByDemandIdOrderByUploadedAtAsc(demandId);
    }

    @Transactional(readOnly = true)
    public Download download(UUID documentId, CurrentUser user) {
        Document doc = documents.findById(documentId).orElseThrow(() -> ApiException.notFound("Documento", documentId));
        Demand demand = demandService.find(doc.getDemandId());
        access.assertCanView(demand, user);
        return new Download(doc.getFileName(), doc.getContentType(), storage.load(doc.getStorageKey()));
    }

    @Transactional(readOnly = true)
    public Document get(UUID documentId) {
        return documents.findById(documentId).orElseThrow(() -> ApiException.notFound("Documento", documentId));
    }

    @Override
    public String code() {
        return "DOCUMENTATION_GENERATED";
    }

    @Override
    public Optional<String> unmetReason(Demand demand) {
        boolean tech = documents.existsByDemandIdAndKind(demand.getId(), Document.Kind.TECHNICAL_DOC);
        boolean client = documents.existsByDemandIdAndKind(demand.getId(), Document.Kind.CLIENT_DOC);
        if (tech && client) {
            return Optional.empty();
        }
        return Optional.of("Documentação pendente: " + (tech ? "" : "técnica ") + (client ? "" : "para o cliente").trim() + ".");
    }

    static String sanitize(String original) {
        String name = original == null ? "arquivo" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[^\\p{L}\\p{N}._ -]", "_").trim();
        if (name.isEmpty() || name.startsWith(".")) {
            name = "arquivo" + name;
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    static String extension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx < 0 ? "" : fileName.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record Download(String fileName, String contentType, byte[] content) {}

    public record DocumentUploaded(UUID demandId, UUID documentId, Document.Kind kind, boolean textExtracted) {}
}
