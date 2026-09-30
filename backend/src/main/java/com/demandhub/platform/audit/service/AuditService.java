package com.demandhub.platform.audit.service;

import com.demandhub.platform.audit.domain.AuditLog;
import com.demandhub.platform.audit.repository.AuditLogRepository;
import com.demandhub.platform.shared.security.CurrentUser;
import com.demandhub.platform.shared.security.SecurityUtils;
import com.demandhub.platform.shared.util.Texts;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Ponto único de gravação de auditoria. Uso:
 * {@code audit.event("CHANGE_PRIORITY").entity("Demand", id).demand(id).change("priority", "P1", "P3").reason(r).record();}
 * O ator é o usuário autenticado, ou "SYSTEM" quando não houver (ex.: webhooks, tarefas assíncronas).
 */
@Service
public class AuditService {

    public static final String SYSTEM = "SYSTEM";

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Entry event(String action) {
        return new Entry(action);
    }

    public final class Entry {
        private final AuditLog log = new AuditLog();
        private String actorOverride;

        private Entry(String action) {
            log.setAction(action);
        }

        public Entry entity(String type, Object id) {
            log.setEntityType(type);
            log.setEntityId(id == null ? null : id.toString());
            return this;
        }

        public Entry demand(UUID demandId) {
            log.setDemandId(demandId);
            return this;
        }

        public Entry change(String field, Object before, Object after) {
            log.setField(field);
            log.setBeforeValue(Texts.truncate(before == null ? null : before.toString(), 4000));
            log.setAfterValue(Texts.truncate(after == null ? null : after.toString(), 4000));
            return this;
        }

        public Entry reason(String reason) {
            log.setReason(Texts.truncate(reason, 2000));
            return this;
        }

        public Entry aiSuggestion(UUID suggestionId) {
            log.setAiSuggestionId(suggestionId);
            return this;
        }

        public Entry metadata(String metadata) {
            log.setMetadata(Texts.truncate(metadata, 4000));
            return this;
        }

        /** Ator não humano explícito, ex.: "SYSTEM" ou "AI:SummaryAgent". */
        public Entry actor(String name) {
            this.actorOverride = name;
            return this;
        }

        public AuditLog record() {
            if (log.getEntityType() == null) {
                log.setEntityType("System");
            }
            CurrentUser user = actorOverride == null ? SecurityUtils.currentUserOptional().orElse(null) : null;
            if (user != null) {
                log.setActorId(user.id());
                log.setActorName(user.fullName());
                log.setActorRoles(user.rolesAsString());
            } else {
                log.setActorName(Objects.requireNonNullElse(actorOverride, SYSTEM));
            }
            log.setCreatedAt(clock.instant());
            return repository.save(log);
        }
    }
}
