package com.demandhub.platform.integration.jira;

import com.demandhub.platform.ai.domain.AiAnalysis;
import com.demandhub.platform.ai.repository.AiRepositories.AiAnalysisRepository;
import com.demandhub.platform.audit.service.AuditService;
import com.demandhub.platform.config.AppProperties;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.repository.DemandRepositories.DemandRepository;
import com.demandhub.platform.integration.common.ExternalLink;
import com.demandhub.platform.integration.common.IntegrationConfig.IntegrationGateways;
import com.demandhub.platform.integration.common.IntegrationMode;
import com.demandhub.platform.integration.common.IntegrationRepositories.ExternalLinkRepository;
import com.demandhub.platform.shared.util.JsonSupport;
import com.demandhub.platform.shared.util.Texts;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sincronização com o Jira PMO — master do lifecycle da DEMANDA.
 * Estágio da plataforma → status Jira (por nome configurado). Execução técnica → apenas comentários.
 * Falhas nunca bloqueiam o workflow: o vínculo fica FAILED, auditado e reprocessável.
 */
@Service
public class JiraSyncService {

    private static final Logger log = LoggerFactory.getLogger(JiraSyncService.class);

    private final IntegrationGateways gateways;
    private final ExternalLinkRepository links;
    private final DemandRepository demands;
    private final AiAnalysisRepository analyses;
    private final AuditService audit;
    private final AppProperties props;
    private final JsonSupport json;
    private final Clock clock;

    public JiraSyncService(IntegrationGateways gateways, ExternalLinkRepository links, DemandRepository demands,
                           AiAnalysisRepository analyses, AuditService audit, AppProperties props, JsonSupport json, Clock clock) {
        this.gateways = gateways;
        this.links = links;
        this.demands = demands;
        this.analyses = analyses;
        this.audit = audit;
        this.props = props;
        this.json = json;
        this.clock = clock;
    }

    public IntegrationMode mode() {
        return gateways.jiraMode();
    }

    @Transactional
    public Optional<ExternalLink> createIssue(UUID demandId) {
        JiraGateway jira = gateways.jira();
        if (jira == null) {
            audit.event("INTEGRATION_SKIPPED").actor(AuditService.SYSTEM).entity("Demand", demandId).demand(demandId)
                    .metadata("system=JIRA; mode=DISABLED").record();
            return Optional.empty();
        }
        Demand d = demands.findById(demandId).orElseThrow();
        ExternalLink link = links.findByDemandIdAndSystem(demandId, ExternalLink.System.JIRA).orElseGet(() -> newLink(demandId, jira.mode()));
        if (link.isCreated()) {
            return Optional.of(link);
        }
        String projectKey = d.getProject() != null && d.getProject().getJiraProjectKey() != null
                ? d.getProject().getJiraProjectKey()
                : Texts.isBlank(props.jira().project()) ? (jira.mode() == IntegrationMode.MOCK ? "PMO" : null) : props.jira().project();
        try {
            if (projectKey == null) {
                throw new JiraGateway.JiraException("Projeto Jira não configurado (project.jiraProjectKey ou JIRA_PROJECT).");
            }
            JiraGateway.CreatedIssue issue = jira.createIssue(new JiraGateway.IssueRequest(projectKey,
                    "[" + d.displayId() + "] " + d.getTitle(), description(d), priorityName(d), labels(d)));
            link.setExternalId(issue.id());
            link.setExternalKey(issue.key());
            link.setUrl(issue.url());
            link.setProjectRef(projectKey);
            markOk(link);
            audit.event("JIRA_ISSUE_CREATED").actor(AuditService.SYSTEM).entity("ExternalLink", link.getId()).demand(demandId)
                    .change("jiraKey", null, issue.key()).metadata("mode=" + jira.mode()).record();
            transition(d, link);
        } catch (RuntimeException e) {
            markFailed(link, e, demandId, "create");
        }
        return Optional.of(link);
    }

    /** Reflete o estágio atual da demanda no status do Jira (por nome configurado no estágio). */
    @Transactional
    public void syncStage(UUID demandId) {
        JiraGateway jira = gateways.jira();
        if (jira == null) return;
        links.findByDemandIdAndSystem(demandId, ExternalLink.System.JIRA).filter(ExternalLink::isCreated)
                .ifPresent(link -> transition(demands.findById(demandId).orElseThrow(), link));
    }

    private void transition(Demand d, ExternalLink link) {
        String target = d.getCurrentStage() == null ? null : d.getCurrentStage().getJiraStatus();
        if (Texts.isBlank(target)) return;
        try {
            boolean moved = gateways.jira().transitionTo(link.getExternalKey(), target);
            markOk(link);
            if (!moved) {
                log.info("Jira {}: nenhuma transição disponível para '{}'", link.getExternalKey(), target);
            }
        } catch (RuntimeException e) {
            markFailed(link, e, d.getId(), "transition");
        }
    }

    @Transactional
    public void syncFields(UUID demandId) {
        JiraGateway jira = gateways.jira();
        if (jira == null) return;
        links.findByDemandIdAndSystem(demandId, ExternalLink.System.JIRA).filter(ExternalLink::isCreated).ifPresent(link -> {
            Demand d = demands.findById(demandId).orElseThrow();
            try {
                jira.updateIssue(link.getExternalKey(), priorityName(d), labels(d));
                markOk(link);
            } catch (RuntimeException e) {
                markFailed(link, e, demandId, "update");
            }
        });
    }

    /** Comentário no Jira PMO (ex.: avanço da execução técnica). Não altera o status mestre. */
    @Transactional
    public void comment(UUID demandId, String text) {
        JiraGateway jira = gateways.jira();
        if (jira == null) return;
        links.findByDemandIdAndSystem(demandId, ExternalLink.System.JIRA).filter(ExternalLink::isCreated).ifPresent(link -> {
            try {
                jira.addComment(link.getExternalKey(), text);
                markOk(link);
                audit.event("JIRA_COMMENT_ADDED").actor(AuditService.SYSTEM).entity("ExternalLink", link.getId()).demand(demandId)
                        .metadata(Texts.truncate(text, 500)).record();
            } catch (RuntimeException e) {
                markFailed(link, e, demandId, "comment");
            }
        });
    }

    /** Reprocessa: cria a issue se ainda não existir; caso contrário, reaplica status e campos. */
    @Transactional
    public Optional<ExternalLink> retry(UUID demandId) {
        Optional<ExternalLink> existing = links.findByDemandIdAndSystem(demandId, ExternalLink.System.JIRA);
        if (existing.isEmpty() || !existing.get().isCreated()) {
            return createIssue(demandId);
        }
        syncFields(demandId);
        syncStage(demandId);
        return existing;
    }

    private ExternalLink newLink(UUID demandId, IntegrationMode mode) {
        ExternalLink link = new ExternalLink();
        link.setDemandId(demandId);
        link.setSystem(ExternalLink.System.JIRA);
        link.setMode(mode);
        link.setSyncStatus(ExternalLink.SyncStatus.OK);
        link.setCreatedAt(clock.instant());
        return links.save(link);
    }

    private void markOk(ExternalLink link) {
        link.setSyncStatus(ExternalLink.SyncStatus.OK);
        link.setLastError(null);
        link.setLastSyncAt(clock.instant());
    }

    private void markFailed(ExternalLink link, RuntimeException e, UUID demandId, String operation) {
        log.warn("Falha na integração Jira ({}) da demanda {}: {}", operation, demandId, e.getMessage());
        link.setSyncStatus(ExternalLink.SyncStatus.FAILED);
        link.setLastError(Texts.truncate(e.getMessage(), 2000));
        link.setLastSyncAt(clock.instant());
        audit.event("INTEGRATION_FAILED").actor(AuditService.SYSTEM).entity("ExternalLink", link.getId()).demand(demandId)
                .metadata("system=JIRA; operation=" + operation + "; error=" + Texts.truncate(e.getMessage(), 500)).record();
    }

    String priorityName(Demand d) {
        if (d.getPriority() == null) return null;
        Map<String, String> map = new LinkedHashMap<>();
        Arrays.stream(props.jira().priorityMap().split(",")).map(s -> s.split(":")).filter(p -> p.length == 2)
                .forEach(p -> map.put(p[0].trim(), p[1].trim()));
        return map.get(d.getPriority().getCode());
    }

    List<String> labels(Demand d) {
        List<String> labels = new ArrayList<>(List.of("demand-hub"));
        if (d.getProject() != null) labels.add("projeto-" + d.getProject().getCode().toLowerCase(Locale.ROOT));
        if (d.getDemandType() != null) labels.add("tipo-" + d.getDemandType().getCode().toLowerCase(Locale.ROOT));
        if (d.getPriority() != null) labels.add(d.getPriority().getCode().toLowerCase(Locale.ROOT));
        return labels;
    }

    private String description(Demand d) {
        String summary = analyses.findFirstByDemandIdAndKindOrderByCreatedAtDesc(d.getId(), AiAnalysis.Kind.TRIAGE)
                .map(a -> json.read(a.effectiveContent()).path("summary").path("executiveSummary").asText(null))
                .orElse(null);
        return """
                Projeto: %s
                Tipo: %s | Prioridade: %s
                Solicitante: %s (%s)
                Patrocinador: %s

                Resumo executivo:
                %s

                Objetivo:
                %s

                Problema atual:
                %s

                Benefícios esperados:
                %s

                Prazo desejado: %s

                Detalhes completos, documentos e histórico: %s/demands/%s
                """.formatted(
                d.getProject() == null ? "-" : d.getProject().getCode() + " — " + d.getProject().getName(),
                d.getDemandType() == null ? "-" : d.getDemandType().getName(),
                d.getPriority() == null ? "-" : d.getPriority().getCode() + " " + d.getPriority().getName(),
                d.getRequester() == null ? "-" : d.getRequester().getFullName(), n(d.getRequesterArea()), n(d.getSponsorName()),
                summary == null ? "(resumo em elaboração)" : summary, n(d.getObjective()), n(d.getCurrentProblem()),
                n(d.getExpectedBenefits()), d.getDesiredDate() == null ? "-" : d.getDesiredDate().toString(),
                props.publicUrl(), d.getId());
    }

    private static String n(String s) {
        return Texts.isBlank(s) ? "-" : s;
    }
}
