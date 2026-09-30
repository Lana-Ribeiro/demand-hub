package com.demandhub.platform.demand.web;

import com.demandhub.platform.demand.service.LegacyImportService;
import com.demandhub.platform.demand.service.LegacyImportService.ImportResult;
import com.demandhub.platform.demand.service.LegacyImportService.LegacyRecord;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LegacyImportController {

    private final LegacyImportService service;

    public LegacyImportController(LegacyImportService service) {
        this.service = service;
    }

    @PostMapping("/api/admin/legacy/import")
    @PreAuthorize("hasAuthority('LEGACY_IMPORT')")
    public ImportResult importLegacy(@Validated @RequestBody ImportRequest req) {
        return service.importRecords(req.records());
    }

    public record ImportRequest(@NotNull @Size(max = 5000) List<LegacyRecord> records) {}
}
