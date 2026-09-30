package com.demandhub.platform.project.service;

import com.demandhub.platform.audit.service.AuditService;
import com.demandhub.platform.identity.repository.UserRepository;
import com.demandhub.platform.project.domain.Project;
import com.demandhub.platform.project.repository.ProjectRepository;
import com.demandhub.platform.project.web.ProjectDtos.ProjectRequest;
import com.demandhub.platform.shared.error.ApiException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final UserRepository users;
    private final AuditService audit;

    public ProjectService(ProjectRepository projects, UserRepository users, AuditService audit) {
        this.projects = projects;
        this.users = users;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Project> list(boolean onlyActive) {
        return onlyActive ? projects.findByActiveTrueOrderByNameAsc() : projects.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Project get(Long id) {
        return projects.findById(id).orElseThrow(() -> ApiException.notFound("Projeto", id));
    }

    @Transactional
    public Project create(ProjectRequest req) {
        if (projects.existsByCodeIgnoreCase(req.code())) {
            throw ApiException.conflict("PROJECT_CODE_IN_USE", "Já existe projeto com o código " + req.code());
        }
        Project p = new Project();
        apply(p, req);
        Project saved = projects.save(p);
        audit.event("PROJECT_CREATED").entity("Project", saved.getId()).change("code", null, saved.getCode()).record();
        return saved;
    }

    @Transactional
    public Project update(Long id, ProjectRequest req) {
        Project p = get(id);
        if (!p.getCode().equalsIgnoreCase(req.code()) && projects.existsByCodeIgnoreCase(req.code())) {
            throw ApiException.conflict("PROJECT_CODE_IN_USE", "Já existe projeto com o código " + req.code());
        }
        String before = describe(p);
        apply(p, req);
        audit.event("PROJECT_UPDATED").entity("Project", id).change("project", before, describe(p)).record();
        return p;
    }

    private void apply(Project p, ProjectRequest req) {
        p.setCode(req.code().toUpperCase());
        p.setName(req.name().trim());
        p.setDescription(req.description());
        p.setColor(req.color().toUpperCase());
        p.setIcon(req.icon());
        p.setOwner(req.ownerId() == null ? null : users.findById(req.ownerId()).orElseThrow(() -> ApiException.notFound("Usuário", req.ownerId())));
        p.setPmo(req.pmoId() == null ? null : users.findById(req.pmoId()).orElseThrow(() -> ApiException.notFound("Usuário", req.pmoId())));
        p.setJiraProjectKey(blankToNull(req.jiraProjectKey()));
        p.setScmProjectRef(blankToNull(req.scmProjectRef()));
        if (req.active() != null) {
            p.setActive(req.active());
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String describe(Project p) {
        return "%s|%s|%s|jira=%s|scm=%s|active=%s".formatted(p.getCode(), p.getName(), p.getColor(),
                p.getJiraProjectKey(), p.getScmProjectRef(), p.isActive());
    }
}
