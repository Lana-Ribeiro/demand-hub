package com.demandhub.platform.audit.web;

import com.demandhub.platform.audit.domain.AuditLog;
import com.demandhub.platform.audit.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasAuthority('AUDIT_VIEW')")
public class AuditController {

    private final AuditLogRepository repository;

    public AuditController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Page<AuditLog> search(@RequestParam(required = false) UUID demandId,
                                 @RequestParam(required = false) String action,
                                 @RequestParam(required = false) String actor,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "50") int size) {
        Specification<AuditLog> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (demandId != null) p.add(cb.equal(root.get("demandId"), demandId));
            if (action != null && !action.isBlank()) p.add(cb.equal(root.get("action"), action));
            if (actor != null && !actor.isBlank()) p.add(cb.like(cb.lower(root.get("actorName")), "%" + actor.toLowerCase() + "%"));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) p.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "createdAt")));
    }
}
