package com.demandhub.platform.demand.service;

import com.demandhub.platform.audit.service.AuditService;
import com.demandhub.platform.demand.domain.Comment;
import com.demandhub.platform.demand.domain.CommentSource;
import com.demandhub.platform.demand.domain.CommentVisibility;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.domain.DemandField;
import com.demandhub.platform.demand.domain.DemandSource;
import com.demandhub.platform.demand.domain.InformationRequest;
import com.demandhub.platform.demand.domain.InformationRequestStatus;
import com.demandhub.platform.demand.domain.LifecycleState;
import com.demandhub.platform.demand.repository.DemandRepositories.CommentRepository;
import com.demandhub.platform.demand.repository.DemandRepositories.DemandRepository;
import com.demandhub.platform.demand.repository.DemandRepositories.InformationRequestRepository;
import com.demandhub.platform.identity.domain.User;
import com.demandhub.platform.identity.repository.UserRepository;
import com.demandhub.platform.shared.error.ApiException;
import com.demandhub.platform.shared.events.DomainEvents.DemandSubmitted;
import com.demandhub.platform.shared.events.DomainEvents.DemandUpdated;
import com.demandhub.platform.shared.events.DomainEvents.InformationProvided;
import com.demandhub.platform.shared.events.DomainEvents.InformationRequested;
import com.demandhub.platform.shared.security.CurrentUser;
import com.demandhub.platform.shared.security.Permissions;
import com.demandhub.platform.shared.util.Texts;
import com.demandhub.platform.workflow.service.WorkflowEngine;
import java.time.Clock;
import java.time.Year;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negócio da demanda: rascunho, envio, edição na triagem, pendências e comentários.
 * Mudanças de estágio são sempre delegadas ao {@link WorkflowEngine}.
 */
@Service
public class DemandService {

    private static final Set<DemandField> TRIAGE_FIELDS = Set.of(DemandField.PRIORITY, DemandField.PROJECT, DemandField.DEMAND_TYPE);

    private final DemandRepository demands;
    private final InformationRequestRepository infoRequests;
    private final CommentRepository comments;
    private final UserRepository users;
    private final DemandFieldAccessor accessor;
    private final DemandSubmissionValidator validator;
    private final DemandAccessPolicy access;
    private final WorkflowEngine engine;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public DemandService(DemandRepository demands, InformationRequestRepository infoRequests, CommentRepository comments,
                         UserRepository users, DemandFieldAccessor accessor, DemandSubmissionValidator validator,
                         DemandAccessPolicy access, WorkflowEngine engine, AuditService audit,
                         ApplicationEventPublisher events, Clock clock) {
        this.demands = demands;
        this.infoRequests = infoRequests;
        this.comments = comments;
        this.users = users;
        this.accessor = accessor;
        this.validator = validator;
        this.access = access;
        this.engine = engine;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- leitura

    @Transactional(readOnly = true)
    public Demand getForView(UUID id, CurrentUser user) {
        Demand d = find(id);
        access.assertCanView(d, user);
        return d;
    }

    public Demand find(UUID id) {
        return demands.findById(id).orElseThrow(() -> ApiException.notFound("Demanda", id));
    }

    // ---------------------------------------------------------------- rascunho

    @Transactional
    public Demand createDraft(Map<String, String> fields, DemandSource source, CurrentUser user) {
        if (!user.has(Permissions.DEMAND_CREATE)) {
            throw ApiException.forbidden("Sem permissão para criar demandas.");
        }
        Demand d = new Demand();
        User requester = users.getReferenceById(user.id());
        d.setRequester(requester);
        d.setSource(source == null ? DemandSource.PORTAL : source);
        d.setLifecycleState(LifecycleState.DRAFT);
        applyClientFields(d, fields == null ? Map.of() : fields);
        if (Texts.isBlank(d.getRequesterArea())) {
            d.setRequesterArea(users.findById(user.id()).map(User::getArea).orElse(null));
        }
        demands.save(d);
        audit.event("DEMAND_CREATED").entity("Demand", d.getId()).demand(d.getId()).metadata("source=" + d.getSource()).record();
        return d;
    }

    @Transactional
    public Demand updateDraft(UUID id, Map<String, String> fields, CurrentUser user) {
        Demand d = find(id);
        access.assertOwnerOfDraft(d, user);
        applyClientFields(d, fields);
        audit.event("DRAFT_UPDATED").entity("Demand", id).demand(id).record();
        return d;
    }

    @Transactional
    public Demand discardDraft(UUID id, CurrentUser user) {
        Demand d = find(id);
        access.assertOwnerOfDraft(d, user);
        d.setLifecycleState(LifecycleState.CANCELLED);
        d.setReadOnly(true);
        audit.event("DRAFT_DISCARDED").entity("Demand", id).demand(id).record();
        return d;
    }

    /** Campos que o cliente preenche: substituição completa (chave ausente = vazio). */
    private void applyClientFields(Demand d, Map<String, String> fields) {
        rejectUnknownKeys(fields);
        for (DemandField f : DemandField.values()) {
            if (f.clientEditable()) {
                accessor.write(d, f, fields.get(f.key()));
            }
        }
    }

    private static void rejectUnknownKeys(Map<String, String> fields) {
        for (String key : fields.keySet()) {
            if (DemandField.byKey(key).isEmpty()) {
                throw ApiException.badRequest("UNKNOWN_FIELD", "Campo desconhecido: " + key);
            }
        }
    }

    // ---------------------------------------------------------------- envio

    @Transactional(readOnly = true)
    public Map<String, String> validateForSubmission(UUID id, CurrentUser user) {
        Demand d = find(id);
        access.assertCanView(d, user);
        return validator.validate(d);
    }

    @Transactional
    public Demand submit(UUID id, CurrentUser user) {
        Demand d = find(id);
        access.assertOwnerOfDraft(d, user);
        Map<String, String> errors = validator.validate(d);
        if (!errors.isEmpty()) {
            throw ApiException.businessRule("SUBMISSION_INCOMPLETE", "Preencha os campos obrigatórios antes de enviar.",
                    Map.of("fieldErrors", errors));
        }
        d.setProtocol("DEM-%d-%05d".formatted(Year.now(clock).getValue(), demands.nextProtocolNumber()));
        d.setSubmittedAt(clock.instant());
        engine.start(d, d.getDemandType().getWorkflow(), user);
        engine.transition(d, "SUBMIT", null, user);
        audit.event("DEMAND_SUBMITTED").entity("Demand", id).demand(id).change("protocol", null, d.getProtocol()).record();
        events.publishEvent(new DemandSubmitted(id));
        return d;
    }

    // ---------------------------------------------------------------- triagem / edição interna

    /**
     * Edição após o envio (PMO). Atualização parcial: somente as chaves enviadas.
     * Cada campo alterado gera auditoria com valor anterior, novo valor e motivo.
     */
    @Transactional
    public Demand updateByStaff(UUID id, Map<String, String> fields, String reason, CurrentUser user) {
        Demand d = find(id);
        access.assertCanView(d, user);
        assertEditableByStaff(d);
        rejectUnknownKeys(fields);
        Set<String> changed = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            DemandField f = DemandField.byKey(e.getKey()).orElseThrow();
            if (changeField(d, f, e.getValue(), reason, null, user)) {
                changed.add(f.key());
            }
        }
        if (!changed.isEmpty()) {
            events.publishEvent(new DemandUpdated(id, changed));
        }
        return d;
    }

    /**
     * Aplica um valor a um campo com as regras de quem pode editar o quê. Usado pela aceitação de sugestões da IA.
     * @return true se o valor mudou
     */
    @Transactional
    public boolean changeField(Demand d, DemandField f, String rawValue, String reason, UUID aiSuggestionId, CurrentUser user) {
        if (d.isDraft()) {
            access.assertOwnerOfDraft(d, user);
            if (!f.clientEditable()) {
                throw ApiException.forbidden("O campo " + f.label() + " é definido pelo PMO.");
            }
        } else {
            assertEditableByStaff(d);
            boolean allowed = TRIAGE_FIELDS.contains(f) ? user.has(Permissions.DEMAND_TRIAGE) : user.has(Permissions.DEMAND_EDIT_ALL);
            if (!allowed) {
                throw ApiException.forbidden("Sem permissão para alterar " + f.label() + ".");
            }
        }
        String before = accessor.read(d, f);
        String after = accessor.normalize(f, rawValue);
        if (Objects.equals(Texts.isBlank(before) ? null : before, after)) {
            return false;
        }
        accessor.write(d, f, after);
        if (f == DemandField.DEMAND_TYPE && !d.isDraft() && d.getDemandType() != null) {
            engine.changeWorkflow(d, d.getDemandType().getWorkflow(), user);
        }
        audit.event(actionFor(f)).entity("Demand", d.getId()).demand(d.getId())
                .change(f.key(), before, after).reason(reason).aiSuggestion(aiSuggestionId).record();
        return true;
    }

    private static String actionFor(DemandField f) {
        return switch (f) {
            case PRIORITY -> "CHANGE_PRIORITY";
            case DEMAND_TYPE -> "CHANGE_TYPE";
            case PROJECT -> "CHANGE_PROJECT";
            default -> "FIELD_CHANGED";
        };
    }

    private static void assertEditableByStaff(Demand d) {
        if (d.isReadOnly()) {
            throw ApiException.businessRule("DEMAND_READ_ONLY", "Demanda somente leitura (legado ou encerrada).");
        }
        if (d.isDraft()) {
            throw ApiException.businessRule("DEMAND_IS_DRAFT", "Rascunhos só podem ser editados pelo solicitante.");
        }
    }

    @Transactional
    public Demand assignOwner(UUID id, UUID ownerId, CurrentUser user) {
        if (!user.has(Permissions.DEMAND_TRIAGE)) {
            throw ApiException.forbidden("Sem permissão para definir responsável.");
        }
        Demand d = find(id);
        assertEditableByStaff(d);
        User owner = ownerId == null ? null : users.findById(ownerId).filter(User::isActive)
                .orElseThrow(() -> ApiException.notFound("Usuário", ownerId));
        String before = d.getOwner() == null ? null : d.getOwner().getFullName();
        d.setOwner(owner);
        audit.event("CHANGE_OWNER").entity("Demand", id).demand(id)
                .change("owner", before, owner == null ? null : owner.getFullName()).record();
        return d;
    }

    @Transactional
    public Demand transition(UUID id, String action, String reason, CurrentUser user) {
        Demand d = find(id);
        access.assertCanView(d, user);
        if ("SUBMIT".equals(action)) {
            return submit(id, user);
        }
        engine.transition(d, action, reason, user);
        return d;
    }

    // ---------------------------------------------------------------- pendências

    @Transactional
    public InformationRequest requestInformation(UUID id, String question, CurrentUser user) {
        if (!user.has(Permissions.DEMAND_TRIAGE)) {
            throw ApiException.forbidden("Sem permissão para solicitar informações.");
        }
        Demand d = find(id);
        assertEditableByStaff(d);
        InformationRequest r = new InformationRequest();
        r.setDemandId(id);
        r.setQuestion(question.trim());
        r.setRequestedBy(users.getReferenceById(user.id()));
        r.setRequestedAt(clock.instant());
        infoRequests.save(r);
        audit.event("INFORMATION_REQUESTED").entity("InformationRequest", r.getId()).demand(id).reason(question).record();
        boolean canMoveToOnHold = engine.available(d, user).stream().anyMatch(t -> t.action().equals("REQUEST_INFO"));
        if (canMoveToOnHold) {
            engine.transition(d, "REQUEST_INFO", question, user);
        }
        events.publishEvent(new InformationRequested(id, r.getId()));
        return r;
    }

    @Transactional
    public InformationRequest answerInformation(UUID id, UUID requestId, String response, CurrentUser user) {
        Demand d = find(id);
        access.assertCanView(d, user);
        boolean isRequester = d.isOwnedBy(user.id());
        if (!user.has(Permissions.DEMAND_RESPOND) || !(isRequester || user.has(Permissions.DEMAND_EDIT_ALL))) {
            throw ApiException.forbidden("Somente o solicitante pode responder a pendência.");
        }
        InformationRequest r = infoRequests.findById(requestId)
                .filter(x -> x.getDemandId().equals(id))
                .orElseThrow(() -> ApiException.notFound("Pendência", requestId));
        if (r.getStatus() != InformationRequestStatus.OPEN) {
            throw ApiException.businessRule("REQUEST_NOT_OPEN", "Esta pendência já foi respondida.");
        }
        r.setResponse(response.trim());
        r.setRespondedBy(users.getReferenceById(user.id()));
        r.setRespondedAt(clock.instant());
        r.setStatus(InformationRequestStatus.ANSWERED);
        audit.event("INFORMATION_PROVIDED").entity("InformationRequest", requestId).demand(id).record();
        events.publishEvent(new InformationProvided(id, requestId));
        engine.tryAutoAdvance(d);
        return r;
    }

    @Transactional(readOnly = true)
    public List<InformationRequest> informationRequests(UUID id, CurrentUser user) {
        getForView(id, user);
        return infoRequests.findByDemandIdOrderByRequestedAtAsc(id);
    }

    // ---------------------------------------------------------------- comentários

    @Transactional
    public Comment addComment(UUID id, String body, CommentVisibility visibility, CurrentUser user) {
        Demand d = getForView(id, user);
        CommentVisibility effective = access.isInternal(user) && visibility != null ? visibility : CommentVisibility.PUBLIC;
        Comment c = new Comment();
        c.setDemandId(d.getId());
        c.setAuthorId(user.id());
        c.setAuthorName(user.fullName());
        c.setBody(body.trim());
        c.setVisibility(effective);
        c.setSource(CommentSource.PLATFORM);
        c.setCreatedAt(clock.instant());
        comments.save(c);
        audit.event("COMMENT_ADDED").entity("Comment", c.getId()).demand(id).metadata("visibility=" + effective).record();
        return c;
    }

    @Transactional(readOnly = true)
    public List<Comment> comments(UUID id, CurrentUser user) {
        getForView(id, user);
        return access.isInternal(user)
                ? comments.findByDemandIdOrderByCreatedAtAsc(id)
                : comments.findByDemandIdAndVisibilityOrderByCreatedAtAsc(id, CommentVisibility.PUBLIC);
    }
}
