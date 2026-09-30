package com.demandhub.platform.catalog.web;

import com.demandhub.platform.audit.service.AuditService;
import com.demandhub.platform.catalog.domain.DemandType;
import com.demandhub.platform.catalog.domain.Priority;
import com.demandhub.platform.catalog.repository.CatalogRepositories.DemandTypeRepository;
import com.demandhub.platform.catalog.repository.CatalogRepositories.PriorityRepository;
import com.demandhub.platform.demand.domain.DemandField;
import com.demandhub.platform.demand.domain.ImpactLevel;
import com.demandhub.platform.demand.domain.Urgency;
import com.demandhub.platform.project.repository.ProjectRepository;
import com.demandhub.platform.project.web.ProjectDtos.ProjectRef;
import com.demandhub.platform.shared.error.ApiException;
import com.demandhub.platform.workflow.repository.WorkflowRepositories.WorkflowDefinitionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Metadados do formulário e catálogos configuráveis (tipos, prioridades). */
@RestController
public class CatalogController {

    private final DemandTypeRepository types;
    private final PriorityRepository priorities;
    private final ProjectRepository projects;
    private final WorkflowDefinitionRepository workflows;
    private final AuditService audit;

    public CatalogController(DemandTypeRepository types, PriorityRepository priorities, ProjectRepository projects,
                             WorkflowDefinitionRepository workflows, AuditService audit) {
        this.types = types;
        this.priorities = priorities;
        this.projects = projects;
        this.workflows = workflows;
        this.audit = audit;
    }

    @GetMapping("/api/catalog")
    @Transactional(readOnly = true)
    public Catalog catalog() {
        return new Catalog(
                Arrays.stream(DemandField.values()).map(f -> new FieldMeta(f.key(), f.label(), f.section().name(), f.type().name(),
                        f.requiredAtSubmit(), f.maxLength(), f.help(), f.clientEditable())).toList(),
                types.findAllByOrderByNameAsc().stream().map(TypeView::from).toList(),
                priorities.findAllByOrderByRankOrderAsc(),
                projects.findByActiveTrueOrderByNameAsc().stream().map(ProjectRef::from).toList(),
                Arrays.stream(ImpactLevel.values()).map(Enum::name).toList(),
                Arrays.stream(Urgency.values()).map(Enum::name).toList());
    }

    @PostMapping("/api/admin/demand-types")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ADMIN_CONFIG')")
    @Transactional
    public TypeView createType(@Valid @RequestBody TypeRequest req) {
        if (types.findByCode(req.code()).isPresent()) {
            throw ApiException.conflict("TYPE_CODE_IN_USE", "Já existe tipo com o código " + req.code());
        }
        DemandType t = new DemandType();
        t.setCode(req.code());
        apply(t, req);
        types.save(t);
        audit.event("DEMAND_TYPE_CREATED").entity("DemandType", t.getId()).change("type", null, t.getCode() + "→" + req.workflowCode()).record();
        return TypeView.from(t);
    }

    @PutMapping("/api/admin/demand-types/{id}")
    @PreAuthorize("hasAuthority('ADMIN_CONFIG')")
    @Transactional
    public TypeView updateType(@PathVariable Long id, @Valid @RequestBody TypeRequest req) {
        DemandType t = types.findById(id).orElseThrow(() -> ApiException.notFound("Tipo de demanda", id));
        String before = t.getName() + "|" + t.getWorkflow().getCode() + "|technical=" + t.isTechnical() + "|active=" + t.isActive();
        apply(t, req);
        audit.event("DEMAND_TYPE_UPDATED").entity("DemandType", id)
                .change("type", before, t.getName() + "|" + t.getWorkflow().getCode() + "|technical=" + t.isTechnical() + "|active=" + t.isActive()).record();
        return TypeView.from(t);
    }

    private void apply(DemandType t, TypeRequest req) {
        t.setName(req.name());
        t.setDescription(req.description());
        t.setTechnical(req.technical());
        t.setActive(req.active() == null || req.active());
        t.setWorkflow(workflows.findByCode(req.workflowCode())
                .orElseThrow(() -> ApiException.badRequest("INVALID_WORKFLOW", "Workflow inexistente: " + req.workflowCode())));
    }

    @PutMapping("/api/admin/priorities/{code}")
    @PreAuthorize("hasAuthority('ADMIN_CONFIG')")
    @Transactional
    public Priority updatePriority(@PathVariable String code, @Valid @RequestBody PriorityRequest req) {
        Priority p = priorities.findById(code).orElseThrow(() -> ApiException.notFound("Prioridade", code));
        String before = p.getName() + "|" + p.getPolicy();
        p.setName(req.name());
        p.setColor(req.color());
        p.setPolicy(req.policy());
        audit.event("PRIORITY_UPDATED").entity("Priority", code).change("priority", before, p.getName() + "|" + p.getPolicy()).record();
        return p;
    }

    public record FieldMeta(String key, String label, String section, String type, boolean requiredAtSubmit, int maxLength,
                            String help, boolean clientEditable) {}

    public record TypeView(Long id, String code, String name, String description, boolean technical, String workflowCode,
                           String workflowName, boolean active) {
        static TypeView from(DemandType t) {
            return new TypeView(t.getId(), t.getCode(), t.getName(), t.getDescription(), t.isTechnical(),
                    t.getWorkflow().getCode(), t.getWorkflow().getName(), t.isActive());
        }
    }

    public record Catalog(List<FieldMeta> fields, List<TypeView> demandTypes, List<Priority> priorities, List<ProjectRef> projects,
                          List<String> impactLevels, List<String> urgencies) {}

    public record TypeRequest(@NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Z0-9_]+$") String code, @NotBlank @Size(max = 200) String name,
                              @Size(max = 1000) String description, boolean technical, @NotBlank String workflowCode, Boolean active) {}

    public record PriorityRequest(@NotBlank @Size(max = 100) String name, @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
                                  @Size(max = 1000) String policy) {}
}
