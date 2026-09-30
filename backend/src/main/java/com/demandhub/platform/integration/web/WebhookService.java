package com.demandhub.platform.integration.web;

import com.demandhub.platform.audit.service.AuditService;
import com.demandhub.platform.config.AppProperties;
import com.demandhub.platform.demand.domain.Comment;
import com.demandhub.platform.demand.domain.CommentSource;
import com.demandhub.platform.demand.domain.CommentVisibility;
import com.demandhub.platform.demand.repository.DemandRepositories.CommentRepository;
import com.demandhub.platform.execution.service.ExecutionService;
import com.demandhub.platform.integration.common.ExternalLink;
import com.demandhub.platform.integration.common.IntegrationRepositories.ExternalLinkRepository;
import com.demandhub.platform.integration.common.IntegrationRepositories.WebhookEventRepository;
import com.demandhub.platform.integration.common.WebhookEvent;
import com.demandhub.platform.integration.jira.JiraGateway;
import com.demandhub.platform.shared.error.ApiException;
import com.demandhub.platform.shared.util.Texts;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Processamento de webhooks. Segredo compartilhado obrigatório, comparado em tempo constante. */
@Service
public class WebhookService {

    private final AppProperties props;
    private final WebhookEventRepository webhookEvents;
    private final ExternalLinkRepository links;
    private final CommentRepository comments;
    private final ExecutionService executions;
    private final AuditService audit;
    private final Clock clock;

    public WebhookService(AppProperties props, WebhookEventRepository webhookEvents, ExternalLinkRepository links,
                          CommentRepository comments, ExecutionService executions, AuditService audit, Clock clock) {
        this.props = props;
        this.webhookEvents = webhookEvents;
        this.links = links;
        this.comments = comments;
        this.executions = executions;
        this.audit = audit;
        this.clock = clock;
    }

    public void verify(String configuredSecret, String provided) {
        if (Texts.isBlank(configuredSecret) || provided == null
                || !MessageDigest.isEqual(configuredSecret.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_TOKEN", "Webhook não autorizado.");
        }
    }

    @Transactional
    public boolean handleGitLab(String token, JsonNode payload) {
        verify(props.gitlab().webhookSecret(), token);
        String kind = payload.path("object_kind").asText("");
        String projectId = payload.path("project").path("id").asText(null);
        String iid = payload.path("object_attributes").path("iid").asText(null);
        WebhookEvent ev = log("GITLAB", kind, projectId + "#" + iid);
        try {
            if (!"issue".equals(kind) || projectId == null || iid == null) {
                ev.setProcessed(false);
                ev.setError("Evento ignorado (tipo não suportado ou incompleto).");
                return false;
            }
            List<String> labels = new ArrayList<>();
            payload.path("labels").forEach(l -> labels.add(l.path("title").asText()));
            boolean applied = executions.processScmIssueEvent(ExternalLink.System.GITLAB, projectId, iid, labels,
                    payload.path("object_attributes").path("state").asText(""), "GITLAB");
            ev.setProcessed(applied);
            if (!applied) ev.setError("Nenhuma execução vinculada ou nenhum status mapeado.");
            return applied;
        } catch (RuntimeException e) {
            ev.setError(Texts.truncate(e.getMessage(), 2000));
            throw e;
        }
    }

    /**
     * GitHub: valida {@code X-Hub-Signature-256} (HMAC-SHA256 do corpo bruto com GITHUB_WEBHOOK_SECRET).
     * Evento "issues" (labeled/unlabeled/closed/reopened/edited): labels {@code status::*} → status técnico.
     */
    @Transactional
    public boolean handleGitHub(String signature, String event, String rawBody, JsonNode payload) {
        verifyGitHubSignature(props.github().webhookSecret(), signature, rawBody);
        String repository = payload.path("repository").path("full_name").asText(null);
        String number = payload.path("issue").path("number").asText(null);
        WebhookEvent ev = log("GITHUB", event + ":" + payload.path("action").asText(""), repository + "#" + number);
        if (!"issues".equals(event) || repository == null || number == null) {
            ev.setError("Evento ignorado (tipo não suportado ou incompleto).");
            return false;
        }
        List<String> labels = new ArrayList<>();
        payload.path("issue").path("labels").forEach(l -> labels.add(l.path("name").asText()));
        boolean applied = executions.processScmIssueEvent(ExternalLink.System.GITHUB, repository, number, labels,
                payload.path("issue").path("state").asText(""), "GITHUB");
        ev.setProcessed(applied);
        if (!applied) ev.setError("Nenhuma execução vinculada ou nenhum status mapeado.");
        return applied;
    }

    void verifyGitHubSignature(String secret, String signature, String rawBody) {
        if (Texts.isBlank(secret) || signature == null || !signature.startsWith("sha256=")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE", "Webhook não autorizado.");
        }
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE", "Webhook não autorizado.");
            }
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public boolean handleJira(String token, JsonNode payload) {
        verify(props.jira().webhookSecret(), token);
        String type = payload.path("webhookEvent").asText("");
        String key = payload.path("issue").path("key").asText(null);
        WebhookEvent ev = log("JIRA", type, key);
        ExternalLink link = key == null ? null : links.findBySystemAndExternalKey(ExternalLink.System.JIRA, key).orElse(null);
        if (link == null) {
            ev.setError("Issue não vinculada a nenhuma demanda.");
            return false;
        }
        switch (type) {
            case "comment_created" -> {
                JsonNode comment = payload.path("comment");
                String body = textOf(comment.path("body"));
                String externalId = comment.path("id").asText(null);
                // Ignora eco dos comentários publicados pela própria plataforma.
                if (body.startsWith(JiraGateway.COMMENT_PREFIX.trim()) || comments.existsByDemandIdAndExternalId(link.getDemandId(), externalId)) {
                    ev.setProcessed(true);
                    return true;
                }
                Comment c = new Comment();
                c.setDemandId(link.getDemandId());
                c.setAuthorName(comment.path("author").path("displayName").asText("Jira"));
                c.setBody(Texts.truncate(body, 8000));
                c.setVisibility(CommentVisibility.INTERNAL);
                c.setSource(CommentSource.JIRA);
                c.setExternalId(externalId);
                c.setCreatedAt(clock.instant());
                comments.save(c);
                ev.setProcessed(true);
            }
            case "jira:issue_updated" -> {
                // Mudança de status feita diretamente no Jira é registrada, mas NÃO move o estágio (gates são da plataforma).
                for (JsonNode item : payload.path("changelog").path("items")) {
                    if ("status".equalsIgnoreCase(item.path("field").asText())) {
                        audit.event("JIRA_STATUS_CHANGED_EXTERNALLY").actor("JIRA").entity("ExternalLink", link.getId())
                                .demand(link.getDemandId()).change("jiraStatus", item.path("fromString").asText(), item.path("toString").asText())
                                .reason("Alteração feita diretamente no Jira; o estágio da plataforma não foi alterado.").record();
                    }
                }
                ev.setProcessed(true);
            }
            default -> ev.setError("Evento ignorado: " + type);
        }
        return ev.isProcessed();
    }

    private WebhookEvent log(String system, String type, String externalId) {
        WebhookEvent ev = new WebhookEvent();
        ev.setSystem(system);
        ev.setEventType(Texts.truncate(type, 100));
        ev.setExternalId(Texts.truncate(externalId, 100));
        ev.setReceivedAt(clock.instant());
        return webhookEvents.save(ev);
    }

    /** Extrai texto de string simples ou Atlassian Document Format. */
    static String textOf(JsonNode node) {
        if (node.isTextual()) return node.asText();
        StringBuilder sb = new StringBuilder();
        collect(node, sb);
        return sb.toString().trim();
    }

    private static void collect(JsonNode node, StringBuilder sb) {
        if (node.has("text")) sb.append(node.get("text").asText());
        if ("paragraph".equals(node.path("type").asText()) && sb.length() > 0) sb.append('\n');
        node.path("content").forEach(child -> collect(child, sb));
    }
}
