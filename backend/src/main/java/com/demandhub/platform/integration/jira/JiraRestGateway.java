package com.demandhub.platform.integration.jira;

import com.demandhub.platform.config.AppProperties;
import com.demandhub.platform.integration.common.IntegrationMode;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integração REAL com Jira Cloud (REST API v3). Autenticação Basic (e-mail + API token).
 * Descrições em Atlassian Document Format (ADF), parágrafo por linha.
 */
public class JiraRestGateway implements JiraGateway {

    private static final Logger log = LoggerFactory.getLogger(JiraRestGateway.class);

    private final RestClient http;
    private final String baseUrl;
    private final String issueType;

    public JiraRestGateway(AppProperties.Jira props) {
        if (blank(props.url()) || blank(props.userEmail()) || blank(props.apiToken())) {
            throw new IllegalStateException("JIRA_URL, JIRA_USER_EMAIL e JIRA_API_TOKEN são obrigatórios para JIRA_MODE=real.");
        }
        this.baseUrl = props.url().replaceAll("/+$", "");
        this.issueType = blank(props.issueType()) ? "Task" : props.issueType();
        String basic = Base64.getEncoder().encodeToString((props.userEmail() + ":" + props.apiToken()).getBytes(StandardCharsets.UTF_8));
        this.http = RestClient.builder().baseUrl(baseUrl)
                .defaultHeader("Authorization", "Basic " + basic)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Override
    public IntegrationMode mode() {
        return IntegrationMode.REAL;
    }

    @Override
    public CreatedIssue createIssue(IssueRequest req) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("project", Map.of("key", req.projectKey()));
        fields.put("summary", req.summary());
        fields.put("issuetype", Map.of("name", issueType));
        fields.put("description", adf(req.description()));
        fields.put("labels", req.labels());
        if (req.priorityName() != null) {
            fields.put("priority", Map.of("name", req.priorityName()));
        }
        try {
            JsonNode res = createWithPriorityFallback(fields);
            String key = res.path("key").asText();
            return new CreatedIssue(res.path("id").asText(), key, baseUrl + "/browse/" + key);
        } catch (RestClientException e) {
            throw new JiraException("Falha ao criar issue no Jira: " + detail(e), e);
        }
    }

    /**
     * Projetos team-managed podem não ter o campo Prioridade na tela da issue: o Jira responde 400 citando "priority".
     * Nesse caso a issue é criada sem prioridade (a prioridade continua registrada na plataforma e nas labels).
     */
    private JsonNode createWithPriorityFallback(Map<String, Object> fields) {
        try {
            return http.post().uri("/rest/api/3/issue").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("fields", fields)).retrieve().body(JsonNode.class);
        } catch (HttpClientErrorException.BadRequest e) {
            if (!fields.containsKey("priority") || !e.getResponseBodyAsString().contains("priority")) {
                throw e;
            }
            log.warn("Projeto Jira sem o campo Prioridade na tela de criação: issue criada sem prioridade.");
            Map<String, Object> withoutPriority = new LinkedHashMap<>(fields);
            withoutPriority.remove("priority");
            return http.post().uri("/rest/api/3/issue").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("fields", withoutPriority)).retrieve().body(JsonNode.class);
        }
    }

    @Override
    public void updateIssue(String issueKey, String priorityName, List<String> labels) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (priorityName != null) fields.put("priority", Map.of("name", priorityName));
        if (labels != null) fields.put("labels", labels);
        try {
            put(issueKey, fields);
        } catch (HttpClientErrorException.BadRequest e) {
            if (priorityName == null || !e.getResponseBodyAsString().contains("priority")) {
                throw new JiraException("Falha ao atualizar issue no Jira: " + detail(e), e);
            }
            fields.remove("priority");
            put(issueKey, fields);
        } catch (RestClientException e) {
            throw new JiraException("Falha ao atualizar issue no Jira: " + detail(e), e);
        }
    }

    private void put(String issueKey, Map<String, Object> fields) {
        http.put().uri("/rest/api/3/issue/{key}", issueKey).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("fields", fields)).retrieve().toBodilessEntity();
    }

    /** Mensagem de erro do Jira (errorMessages/errors) para exibir no painel de sincronização. */
    private static String detail(RestClientException e) {
        if (e instanceof HttpClientErrorException h) {
            String body = h.getResponseBodyAsString();
            return h.getStatusCode().value() + " " + (body.length() > 500 ? body.substring(0, 500) : body);
        }
        return e.getMessage();
    }

    @Override
    public void addComment(String issueKey, String text) {
        try {
            http.post().uri("/rest/api/3/issue/{key}/comment", issueKey).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("body", adf(COMMENT_PREFIX + text))).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new JiraException("Falha ao comentar no Jira: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean transitionTo(String issueKey, String statusName) {
        try {
            JsonNode res = http.get().uri("/rest/api/3/issue/{key}/transitions", issueKey).retrieve().body(JsonNode.class);
            for (JsonNode t : res.path("transitions")) {
                if (statusName.equalsIgnoreCase(t.path("to").path("name").asText()) || statusName.equalsIgnoreCase(t.path("name").asText())) {
                    http.post().uri("/rest/api/3/issue/{key}/transitions", issueKey).contentType(MediaType.APPLICATION_JSON)
                            .body(Map.of("transition", Map.of("id", t.path("id").asText()))).retrieve().toBodilessEntity();
                    return true;
                }
            }
            return false;
        } catch (RestClientException e) {
            throw new JiraException("Falha ao transicionar issue no Jira: " + e.getMessage(), e);
        }
    }

    /** Converte texto simples em Atlassian Document Format. */
    static Map<String, Object> adf(String text) {
        List<Map<String, Object>> paragraphs = new ArrayList<>();
        for (String line : (text == null ? "" : text).split("\\R")) {
            List<Map<String, Object>> content = line.isBlank() ? List.of() : List.of(Map.of("type", "text", "text", line));
            paragraphs.add(Map.of("type", "paragraph", "content", content));
        }
        return Map.of("type", "doc", "version", 1, "content", paragraphs);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
