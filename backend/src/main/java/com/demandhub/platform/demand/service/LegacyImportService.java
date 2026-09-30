package com.demandhub.platform.demand.service;

import com.demandhub.platform.audit.service.AuditService;
import com.demandhub.platform.catalog.repository.CatalogRepositories.DemandTypeRepository;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.domain.DemandSource;
import com.demandhub.platform.demand.domain.LifecycleState;
import com.demandhub.platform.demand.repository.DemandRepositories.DemandRepository;
import com.demandhub.platform.project.repository.ProjectRepository;
import com.demandhub.platform.shared.util.Texts;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Importação de demandas históricas. Nenhum histórico é recriado: sem workflow, sem estágios, somente leitura,
 * com os dados originais que existirem (campos ausentes permanecem nulos).
 */
@Service
public class LegacyImportService {

    private final DemandRepository demands;
    private final ProjectRepository projects;
    private final DemandTypeRepository types;
    private final AuditService audit;

    public LegacyImportService(DemandRepository demands, ProjectRepository projects, DemandTypeRepository types, AuditService audit) {
        this.demands = demands;
        this.projects = projects;
        this.types = types;
        this.audit = audit;
    }

    public record LegacyRecord(String originalId, String title, String objective, Instant originalCreatedAt, String originalStatus,
                               String originalOwner, String projectCode, String typeCode, String requesterArea, String notes) {}

    public record ImportResult(int imported, int skipped, List<String> messages) {}

    @Transactional
    public ImportResult importRecords(List<LegacyRecord> records) {
        int imported = 0;
        int skipped = 0;
        List<String> messages = new ArrayList<>();
        for (LegacyRecord r : records) {
            if (Texts.isBlank(r.originalId()) || Texts.isBlank(r.title())) {
                skipped++;
                messages.add("Registro ignorado: originalId e title são obrigatórios.");
                continue;
            }
            if (demands.findByOriginalId(r.originalId()).isPresent()) {
                skipped++;
                messages.add("Já importado: " + r.originalId());
                continue;
            }
            Demand d = new Demand();
            d.setSource(DemandSource.LEGACY);
            d.setLifecycleState(LifecycleState.LEGACY);
            d.setReadOnly(true);
            d.setTitle(Texts.truncate(r.title(), 200));
            d.setObjective(r.objective());
            d.setOriginalId(Texts.truncate(r.originalId(), 100));
            d.setOriginalCreatedAt(r.originalCreatedAt());
            d.setOriginalStatus(Texts.truncate(r.originalStatus(), 200));
            d.setOriginalOwner(Texts.truncate(r.originalOwner(), 200));
            d.setRequesterArea(Texts.truncate(r.requesterArea(), 200));
            d.setAdditionalNotes(r.notes());
            if (!Texts.isBlank(r.projectCode())) {
                projects.findByCodeIgnoreCase(r.projectCode()).ifPresentOrElse(d::setProject,
                        () -> messages.add(r.originalId() + ": projeto " + r.projectCode() + " não encontrado (mantido vazio)."));
            }
            if (!Texts.isBlank(r.typeCode())) {
                types.findByCode(r.typeCode().toUpperCase()).ifPresent(d::setDemandType);
            }
            demands.save(d);
            audit.event("LEGACY_IMPORTED").entity("Demand", d.getId()).demand(d.getId()).change("originalId", null, r.originalId()).record();
            imported++;
        }
        return new ImportResult(imported, skipped, messages);
    }
}
