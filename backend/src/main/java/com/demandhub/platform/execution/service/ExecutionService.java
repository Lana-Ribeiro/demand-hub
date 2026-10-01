package com.demandhub.platform.execution.service;

import com.demandhub.platform.ai.agent.technical.TechnicalContext;
import com.demandhub.platform.ai.domain.AiAnalysis;
import com.demandhub.platform.ai.repository.AiRepositories.AiAnalysisRepository;
import com.demandhub.platform.ai.service.TechnicalContextBuilder;
import com.demandhub.platform.audit.service.AuditService;
import com.demandhub.platform.config.AppProperties;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.repository.DemandRepositories.DemandRepository;
import com.demandhub.platform.demand.service.DemandService;
import com.demandhub.platform.execution.domain.ExecutionEvent;
import com.demandhub.platform.execution.domain.ExecutionStatus;
import com.demandhub.platform.execution.domain.TechnicalExecution;
import com.demandhub.platform.execution.repository.ExecutionRepositories.ExecutionEventRepository;
import com.demandhub.platform.execution.repository.ExecutionRepositories.ExecutionStatusRepository;
import com.demandhub.platform.execution.repository.ExecutionRepositories.TechnicalExecutionRepository;
import com.demandhub.platform.integration.common.ExternalLink;
import com.demandhub.platform.integration.common.IntegrationConfig.IntegrationGateways;
import com.demandhub.platform.integration.common.IntegrationMode;
import com.demandhub.platform.integration.common.IntegrationRepositories.ExternalLinkRepository;
import com.demandhub.platform.integration.scm.ScmGateway;
import com.demandhub.platform.integration.jira.JiraSyncService;
import com.demandhub.platform.shared.error.ApiException;
import com.demandhub.platform.shared.events.DomainEvents.ExecutionStatusChanged;
import com.demandhub.platform.shared.security.CurrentUser;
import com.demandhub.platform.shared.security.Permissions;
import com.demandhub.platform.shared.util.JsonSupport;
import com.demandhub.platform.shared.util.Texts;
import com.demandhub.platform.workflow.service.ExitRequirementChecker;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Execução técnica — lifecycle próprio, cuja fonte é o repositório de código (GitLab ou GitHub). Mudanças de status NUNCA alteram o estágio da demanda:
 * geram evento, comentário no Jira PMO (template configurado) e notificação. O gate EXECUTION_DONE libera o avanço humano.
 */
@Service
public class ExecutionService implements ExitRequirementChecker {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final TechnicalExecutionRepository executions;
    private final ExecutionEventRepository events;
    private final ExecutionStatusRepository statuses;
    private final ExternalLinkRepository links;
    private final DemandRepository demands;
    private final DemandService demandService;
    private final IntegrationGateways gateways;
    private final JiraSyncService jira;
    private final TechnicalContextBuilder contextBuilder;
    private final AiAnalysisRepository analyses;
    private final AuditService audit;
    private final ApplicationEventPublisher publisher;
    private final AppProperties props;
    private final JsonSupport json;
    private final Clock clock;

    public ExecutionService(TechnicalExecutionRepository executions, ExecutionEventRepository events, ExecutionStatusRepository statuses,
                            ExternalLinkRepository links, DemandRepository demands, DemandService demandService,
                            IntegrationGateways gateways, JiraSyncService jira, TechnicalContextBuilder contextBuilder,
                            AiAnalysisRepository analyses, AuditService audit, ApplicationEventPublisher publisher,
                            AppProperties props, JsonSupport json, Clock clock) {
        this.executions = executions;
        this.events = events;
        this.statuses = statuses;
        this.links = links;
        this.demands = demands;
        this.demandService = demandService;
        this.gateways = gateways;
        this.jira = jira;
        this.contextBuilder = contextBuilder;
        this.analyses = analyses;
        this.audit = audit;
        this.publisher = publisher;
        this.props = props;
        this.json = json;
        this.clock = clock;
    }

    /** Ação de estágio CREATE_SCM_ISSUE. Somente para tipos técnicos — demandas não técnicas nunca geram issue no repositório. */
    @Transactional
    public Optional<TechnicalExecution> createForDemand(UUID demandId) {
        Demand d = demands.findById(demandId).orElseThrow();
        if (d.getDemandType() == null || !d.getDemandType().isTechnical()) {
            log.info("Demanda {} não é técnica: execução técnica não criada.", d.displayId());
            return Optional.empty();
        }
        Optional<TechnicalExecution> existing = executions.findByDemandId(demandId);
        if (existing.isPresent()) {
            return existing;
        }
        ExecutionStatus initial = statuses.findFirstByOrderByPositionAsc().orElseThrow();
        TechnicalExecution exec = new TechnicalExecution();
        exec.setDemandId(demandId);
        exec.setStatusCode(initial.getCode());
        exec.setStartedAt(clock.instant());
        exec.setLastEventAt(clock.instant());
        executions.save(exec);

        ScmGateway scm = gateways.scm();
        exec.setSystem(gateways.scmSystem().name());
        if (scm == null) {
            audit.event("INTEGRATION_SKIPPED").actor(AuditService.SYSTEM).entity("TechnicalExecution", exec.getId()).demand(demandId)
                    .metadata("system=" + gateways.scmSystem() + "; mode=DISABLED — status técnico será registrado manualmente").record();
        } else {
            createScmIssue(d, exec, scm, initial);
        }
        audit.event("EXECUTION_CREATED").actor(AuditService.SYSTEM).entity("TechnicalExecution", exec.getId()).demand(demandId)
                .change("executionStatus", null, initial.getCode()).record();
        jira.comment(demandId, initial.getJiraCommentTemplate());
        return Optional.of(exec);
    }

    /** Referência do repositório: do projeto (GitLab: id; GitHub: owner/repo) ou o padrão configurado. */
    private String projectRef(Demand d, ScmGateway scm) {
        if (d.getProject() != null && !Texts.isBlank(d.getProject().getScmProjectRef())) {
            return d.getProject().getScmProjectRef();
        }
        String configured = scm.system() == ExternalLink.System.GITHUB ? props.github().repository() : props.gitlab().defaultProjectId();
        if (!Texts.isBlank(configured)) {
            return configured;
        }
        return scm.mode() == IntegrationMode.MOCK ? (scm.system() == ExternalLink.System.GITHUB ? "mock-owner/mock-repo" : "mock-project") : null;
    }

    private void createScmIssue(Demand d, TechnicalExecution exec, ScmGateway scm, ExecutionStatus initial) {
        ExternalLink link = new ExternalLink();
        link.setDemandId(d.getId());
        link.setSystem(scm.system());
        link.setMode(scm.mode());
        link.setSyncStatus(ExternalLink.SyncStatus.OK);
        link.setCreatedAt(clock.instant());
        String projectRef = projectRef(d, scm);
        link.setProjectRef(projectRef);
        links.save(link);
        exec.setExternalLinkId(link.getId());
        try {
            if (projectRef == null) {
                throw new IllegalStateException("Repositório não configurado (referência do repositório no projeto, "
                        + (scm.system() == ExternalLink.System.GITHUB ? "ou GITHUB_REPOSITORY)." : "ou GITLAB_DEFAULT_PROJECT_ID)."));
            }
            ScmGateway.CreatedIssue issue = scm.createIssue(projectRef, "[" + d.displayId() + "] " + d.getTitle(),
                    issueDescription(d), List.of("demand-hub", initial.getScmLabel()));
            link.setExternalId(issue.id());
            link.setExternalKey(issue.key());
            link.setUrl(issue.url());
            link.setLastSyncAt(clock.instant());
            audit.event("SCM_ISSUE_CREATED").actor(AuditService.SYSTEM).entity("ExternalLink", link.getId()).demand(d.getId())
                    .change("issue", null, projectRef + "#" + issue.key()).metadata("system=" + scm.system() + "; mode=" + scm.mode()).record();
            addToBoard(scm, link, issue, initial);
        } catch (RuntimeException e) {
            log.warn("Falha ao criar issue no {} para {}: {}", scm.system(), d.displayId(), e.getMessage());
            link.setSyncStatus(ExternalLink.SyncStatus.FAILED);
            link.setLastError(Texts.truncate(e.getMessage(), 2000));
            audit.event("INTEGRATION_FAILED").actor(AuditService.SYSTEM).entity("ExternalLink", link.getId()).demand(d.getId())
                    .metadata("system=" + scm.system() + "; operation=create; error=" + Texts.truncate(e.getMessage(), 500)).record();
        }
    }

    /** Coloca o card no board (GitHub Projects), quando configurado. Falha no board é registrada sem desfazer a issue. */
    private void addToBoard(ScmGateway scm, ExternalLink link, ScmGateway.CreatedIssue issue, ExecutionStatus column) {
        if (!scm.hasBoard()) {
            return;
        }
        try {
            link.setBoardItemId(scm.addToBoard(issue, column.getName()));
            link.setLastError(null);
            audit.event("SCM_BOARD_ITEM_ADDED").actor(AuditService.SYSTEM).entity("ExternalLink", link.getId()).demand(link.getDemandId())
                    .change("boardColumn", null, column.getName()).record();
        } catch (RuntimeException e) {
            log.warn("Issue criada, mas não foi possível colocá-la no board: {}", e.getMessage());
            link.setLastError(Texts.truncate("Board: " + e.getMessage(), 2000));
            audit.event("INTEGRATION_FAILED").actor(AuditService.SYSTEM).entity("ExternalLink", link.getId()).demand(link.getDemandId())
                    .metadata("system=" + scm.system() + "; operation=board; error=" + Texts.truncate(e.getMessage(), 500)).record();
        }
    }

    /**
     * Movimentos feitos no board (lidos periodicamente — boards pessoais do GitHub não enviam webhook).
     * Coluna → status técnico pelo NOME configurado; mesmo processador de status dos webhooks.
     * @return quantidade de execuções atualizadas
     */
    @Transactional
    public int syncFromBoard() {
        ScmGateway scm = gateways.scm();
        if (scm == null || !scm.hasBoard()) {
            return 0;
        }
        Map<String, TechnicalExecution> byItem = new HashMap<>();
        Map<UUID, ExternalLink> linkByExec = new HashMap<>();
        for (TechnicalExecution exec : executions.findAll()) {
            if (exec.getExternalLinkId() == null || exec.getCompletedAt() != null) continue;
            links.findById(exec.getExternalLinkId()).filter(l -> l.getBoardItemId() != null).ifPresent(l -> {
                byItem.put(l.getBoardItemId(), exec);
                linkByExec.put(exec.getId(), l);
            });
        }
        if (byItem.isEmpty()) {
            return 0;
        }
        Map<String, ExecutionStatus> statusByName = new HashMap<>();
        statuses.findAll().forEach(st -> statusByName.put(st.getName().toLowerCase(java.util.Locale.ROOT), st));
        int updated = 0;
        for (Map.Entry<String, String> e : scm.readBoardColumns(byItem.keySet()).entrySet()) {
            TechnicalExecution exec = byItem.get(e.getKey());
            ExecutionStatus target = statusByName.get(e.getValue().toLowerCase(java.util.Locale.ROOT));
            if (exec == null || target == null || target.getCode().equals(exec.getStatusCode())) continue;
            applyStatus(exec, target, scm.system().name() + "_BOARD", null);
            linkByExec.get(exec.getId()).setLastSyncAt(clock.instant());
            updated++;
        }
        return updated;
    }

    /** Reprocessa a criação da issue no repositório após falha. */
    @Transactional
    public TechnicalExecution retryScm(UUID demandId, CurrentUser user) {
        if (!user.has(Permissions.INTEGRATION_MANAGE) && !user.has(Permissions.EXECUTION_MANAGE)) {
            throw ApiException.forbidden("Sem permissão para reprocessar integrações.");
        }
        TechnicalExecution exec = executions.findByDemandId(demandId).orElseThrow(() -> ApiException.notFound("Execução técnica", demandId));
        ScmGateway scm = gateways.scm();
        if (scm == null) {
            throw ApiException.businessRule("INTEGRATION_DISABLED", "Integração com " + gateways.scmName() + " desabilitada.");
        }
        ExternalLink link = exec.getExternalLinkId() == null ? null : links.findById(exec.getExternalLinkId()).orElse(null);
        if (link != null && link.isCreated() && (link.getMode() != scm.mode() || link.getSystem() != scm.system())) {
            // Vínculo criado em outro modo/sistema (ex.: MOCK): a issue não existe no repositório atual — recria.
            audit.event("SCM_LINK_RESET").actor(AuditService.SYSTEM).entity("ExternalLink", link.getId()).demand(demandId)
                    .change("issue", link.getProjectRef() + "#" + link.getExternalKey() + " (" + link.getMode() + ")", null)
                    .reason("Integração passou para " + scm.system() + " " + scm.mode() + "; a issue será criada novamente.").record();
            link.setExternalKey(null);
        }
        if (link != null && link.isCreated()) {
            if (scm.hasBoard() && link.getBoardItemId() == null) {
                addToBoard(scm, link, new ScmGateway.CreatedIssue(link.getExternalId(), link.getExternalKey(), link.getUrl()),
                        statuses.findById(exec.getStatusCode()).orElseThrow());
            }
            return exec;
        }
        if (link != null) {
            exec.setExternalLinkId(null);
            links.delete(link);
            links.flush();
        }
        createScmIssue(demands.findById(demandId).orElseThrow(), exec, scm, statuses.findById(exec.getStatusCode()).orElseThrow());
        return exec;
    }

    /**
     * Processa um evento de issue do repositório (webhook real do GitLab/GitHub ou simulação MOCK — mesmo caminho).
     * Labels "status::*" → status técnico configurado; issue fechada sem label de status → status "concluído".
     */
    @Transactional
    public boolean processScmIssueEvent(ExternalLink.System system, String projectRef, String issueKey, Collection<String> labels,
                                        String state, String source) {
        Optional<ExternalLink> link = links.findBySystemAndProjectRefAndExternalKey(system, projectRef, issueKey);
        if (link.isEmpty()) {
            return false;
        }
        TechnicalExecution exec = executions.findByExternalLinkId(link.get().getId()).orElse(null);
        if (exec == null) {
            return false;
        }
        Optional<ExecutionStatus> target = labels.stream()
                .filter(l -> l.toLowerCase().startsWith("status::"))
                .map(statuses::findByScmLabelIgnoreCase).flatMap(Optional::stream)
                .max((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));
        if (target.isEmpty() && "closed".equalsIgnoreCase(state)) {
            target = statuses.findFirstByDoneTrueOrderByPositionAsc();
        }
        target.ifPresent(s -> applyStatus(exec, s, source, null));
        return target.isPresent();
    }

    /** Registro manual — permitido somente quando a integração com o repositório está DESABILITADA. */
    @Transactional
    public TechnicalExecution manualStatus(UUID demandId, String statusCode, String note, CurrentUser user) {
        if (!user.has(Permissions.EXECUTION_MANAGE) && !(user.has(Permissions.QA_RECORD) && "QA".equals(statusCode))) {
            throw ApiException.forbidden("Sem permissão para registrar status técnico.");
        }
        if (gateways.scmMode() != IntegrationMode.DISABLED) {
            throw ApiException.businessRule("SCM_IS_SOURCE", "Com a integração " + gateways.scmName()
                    + " ativa, o status técnico vem exclusivamente do " + gateways.scmName() + ".");
        }
        TechnicalExecution exec = executions.findByDemandId(demandId).orElseThrow(() -> ApiException.notFound("Execução técnica", demandId));
        ExecutionStatus status = statuses.findById(statusCode).orElseThrow(() -> ApiException.badRequest("INVALID_STATUS", "Status inválido: " + statusCode));
        applyStatus(exec, status, "PLATFORM", note);
        return exec;
    }

    /** Simulação explícita (somente modo MOCK) pelo mesmo processador dos webhooks. */
    @Transactional
    public TechnicalExecution simulateScmStatus(UUID demandId, String statusCode, CurrentUser user) {
        if (!user.has(Permissions.EXECUTION_MANAGE)) {
            throw ApiException.forbidden("Sem permissão para simular eventos do repositório.");
        }
        if (gateways.scmMode() != IntegrationMode.MOCK) {
            throw ApiException.businessRule("NOT_MOCK_MODE", "Simulação disponível apenas com a integração " + gateways.scmName() + " em modo mock.");
        }
        TechnicalExecution exec = executions.findByDemandId(demandId).orElseThrow(() -> ApiException.notFound("Execução técnica", demandId));
        ExternalLink link = links.findById(exec.getExternalLinkId()).orElseThrow();
        ExecutionStatus status = statuses.findById(statusCode).orElseThrow(() -> ApiException.badRequest("INVALID_STATUS", "Status inválido: " + statusCode));
        processScmIssueEvent(link.getSystem(), link.getProjectRef(), link.getExternalKey(), List.of(status.getScmLabel()), "open",
                link.getSystem().name() + "_MOCK");
        return exec;
    }

    @Transactional
    public TechnicalExecution setStrategy(UUID demandId, TechnicalExecution.Strategy strategy, CurrentUser user) {
        if (!user.has(Permissions.EXECUTION_MANAGE) && !user.has(Permissions.ARCHITECTURE_MANAGE)) {
            throw ApiException.forbidden("Sem permissão para definir a estratégia de desenvolvimento.");
        }
        TechnicalExecution exec = executions.findByDemandId(demandId).orElseThrow(() -> ApiException.notFound("Execução técnica", demandId));
        TechnicalExecution.Strategy before = exec.getStrategy();
        exec.setStrategy(strategy);
        audit.event("EXECUTION_STRATEGY_CHANGED").entity("TechnicalExecution", exec.getId()).demand(demandId).change("strategy", before, strategy).record();
        if (strategy == TechnicalExecution.Strategy.AGENT_SQUAD) {
            postToIssue(exec, analyses.findFirstByDemandIdAndKindOrderByCreatedAtDesc(demandId, AiAnalysis.Kind.SQUAD_PLAN)
                    .map(a -> "Plano do squad de agentes (execução autônoma não habilitada — runner externo):\n\n```json\n"
                            + a.effectiveContent() + "\n```").orElse("Estratégia definida: squad de agentes. Plano ainda não gerado."));
        }
        return exec;
    }

    private void applyStatus(TechnicalExecution exec, ExecutionStatus status, String source, String note) {
        if (status.getCode().equals(exec.getStatusCode())) {
            return;
        }
        String from = exec.getStatusCode();
        ExecutionEvent ev = new ExecutionEvent();
        ev.setExecutionId(exec.getId());
        ev.setFromStatus(from);
        ev.setToStatus(status.getCode());
        ev.setSource(source);
        ev.setSummary(note == null ? status.getJiraCommentTemplate() : status.getJiraCommentTemplate() + " Observação: " + note);
        ev.setReceivedAt(clock.instant());
        events.save(ev);
        exec.setStatusCode(status.getCode());
        exec.setLastEventAt(clock.instant());
        exec.setCompletedAt(status.isDone() ? clock.instant() : null);
        audit.event("EXECUTION_STATUS_CHANGED").actor(source.startsWith("GITLAB") || source.startsWith("GITHUB") ? source.replace("_MOCK", "") : AuditService.SYSTEM)
                .entity("TechnicalExecution", exec.getId()).demand(exec.getDemandId())
                .change("executionStatus", from, status.getCode()).metadata("source=" + source).reason(note).record();
        // Jira PMO recebe COMENTÁRIO — o status mestre da demanda não muda.
        jira.comment(exec.getDemandId(), ev.getSummary());
        mirrorToRepository(exec, from, status, source);
        publisher.publishEvent(new ExecutionStatusChanged(exec.getDemandId(), exec.getId(), from, status.getCode(), status.isDone()));
    }

    /**
     * Espelha o novo status no repositório: coluna do board (se a mudança não veio do board) e label de status
     * (se não veio de label). Falhas aqui não revertem o status — ficam no log e no vínculo.
     */
    private void mirrorToRepository(TechnicalExecution exec, String from, ExecutionStatus status, String source) {
        ScmGateway scm = gateways.scm();
        if (scm == null || exec.getExternalLinkId() == null || source.endsWith("_MOCK")) return;
        links.findById(exec.getExternalLinkId()).filter(ExternalLink::isCreated).ifPresent(link -> {
            try {
                if (!source.endsWith("_BOARD") && link.getBoardItemId() != null) {
                    scm.moveOnBoard(link.getBoardItemId(), status.getName());
                }
                if (!source.equals(link.getSystem().name())) {
                    String oldLabel = from == null ? null : statuses.findById(from).map(ExecutionStatus::getScmLabel).orElse(null);
                    scm.replaceStatusLabel(link.getProjectRef(), link.getExternalKey(), oldLabel, status.getScmLabel());
                }
            } catch (RuntimeException e) {
                log.warn("Status {} aplicado, mas não espelhado no repositório: {}", status.getCode(), e.getMessage());
                link.setLastError(Texts.truncate("Espelhamento: " + e.getMessage(), 2000));
            }
        });
    }

    private void postToIssue(TechnicalExecution exec, String body) {
        ScmGateway scm = gateways.scm();
        if (scm == null || exec.getExternalLinkId() == null) return;
        links.findById(exec.getExternalLinkId()).filter(ExternalLink::isCreated).ifPresent(link -> {
            try {
                scm.addNote(link.getProjectRef(), link.getExternalKey(), body);
            } catch (RuntimeException e) {
                log.warn("Falha ao comentar no repositório: {}", e.getMessage());
            }
        });
    }

    private String issueDescription(Demand d) {
        TechnicalContext c = contextBuilder.build(d);
        String prompt = analyses.findFirstByDemandIdAndKindOrderByCreatedAtDesc(d.getId(), AiAnalysis.Kind.TECH_PROMPT)
                .map(a -> json.read(a.effectiveContent()).path("markdown").asText(null)).orElse(null);
        return """
                ## Demanda %s — %s

                **Jira PMO:** %s · **Plataforma:** %s/demands/%s

                > O lifecycle técnico desta issue é independente do lifecycle da demanda no Jira PMO.
                > Use as labels `status::*` para refletir o andamento; a plataforma comenta o avanço no Jira.

                ### Contexto
                %s

                ### Objetivo
                %s

                ### Requisitos e critérios de aceite
                %s

                ### Decisões do refinamento/arquitetura
                %s

                ### Arquitetura
                %s

                %s
                """.formatted(c.protocol(), c.title(), c.jiraKey() == null ? "-" : c.jiraKey(), props.publicUrl(), d.getId(),
                n(c.problem()), n(c.objective()),
                c.requirements().stream().map(i -> "- [" + i.type() + "] " + i.description()).collect(Collectors.joining("\n")),
                c.decisions().stream().map(i -> "- [" + i.type() + "] " + i.description()).collect(Collectors.joining("\n")),
                n(c.architecture()), prompt == null ? "" : "### Prompt técnico\n\n" + prompt);
    }

    private static String n(String s) {
        return Texts.isBlank(s) ? "Não informado" : s;
    }

    // --------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public Optional<TechnicalExecution> forDemand(UUID demandId, CurrentUser user) {
        demandService.getForView(demandId, user);
        return executions.findByDemandId(demandId);
    }

    @Transactional(readOnly = true)
    public List<ExecutionEvent> eventsOf(UUID executionId) {
        return events.findByExecutionIdOrderByReceivedAtAsc(executionId);
    }

    @Transactional(readOnly = true)
    public List<ExecutionStatus> statuses() {
        return statuses.findAllByOrderByPositionAsc();
    }

    @Override
    public String code() {
        return "EXECUTION_DONE";
    }

    @Override
    public Optional<String> unmetReason(Demand demand) {
        Optional<TechnicalExecution> exec = executions.findByDemandId(demand.getId());
        if (exec.isEmpty()) {
            return Optional.of("Execução técnica ainda não criada.");
        }
        boolean done = statuses.findById(exec.get().getStatusCode()).map(ExecutionStatus::isDone).orElse(false);
        return done ? Optional.empty() : Optional.of("Execução técnica ainda não concluída no repositório (status atual: " + exec.get().getStatusCode() + ").");
    }
}
