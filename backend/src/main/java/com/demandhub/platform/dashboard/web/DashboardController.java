package com.demandhub.platform.dashboard.web;

import com.demandhub.platform.dashboard.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    public DashboardService.Dashboard dashboard(@RequestParam(required = false) Long projectId) {
        return service.build(projectId);
    }
}
