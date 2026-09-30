package com.demandhub.platform.project.web;

import com.demandhub.platform.project.service.ProjectService;
import com.demandhub.platform.project.web.ProjectDtos.ProjectRequest;
import com.demandhub.platform.project.web.ProjectDtos.ProjectResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ProjectResponse> list(@RequestParam(defaultValue = "true") boolean onlyActive) {
        return service.list(onlyActive).stream().map(ProjectResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ProjectResponse get(@PathVariable Long id) {
        return ProjectResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ADMIN_CONFIG')")
    @Transactional
    public ProjectResponse create(@Valid @RequestBody ProjectRequest req) {
        return ProjectResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN_CONFIG')")
    @Transactional
    public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody ProjectRequest req) {
        return ProjectResponse.from(service.update(id, req));
    }
}
