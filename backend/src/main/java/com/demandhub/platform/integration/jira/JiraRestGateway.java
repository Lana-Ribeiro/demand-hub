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
import org.springframework.web.client.RestClientException;

/**
 * Integração REAL com Jira Cloud (REST API v3). Autenticação Basic (e-mail + API token).
 * Descrições em Atlassian Document Format (ADF), parágrafo por linha.
 */
public class JiraRestGateway implements JiraGateway {

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
            JsonNode res = http.post().uri("/rest/api/3/issue").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("fields", fields)).retrieve().body(JsonNode.class);
            String key = res.path("key").asText();
            return new CreatedIssue(res.path("id").asText(), key, baseUrl + "/browse/" + key);
        } catch (RestClientException e) {
            throw new JiraException("Falha ao criar issue no Jira: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateIssue(String issueKey, String priorityName, List<String> labels) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (priorityName != null) fields.put("priority", Map.of("name", priorityName));
        if (labels != null) fields.put("labels", labels);
        try {
            http.put().uri("/rest/api/3/issue/{key}", issueKey).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("fields", fields)).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new JiraException("Falha ao atualizar issue no Jira: " + e.getMessage(), e);
        }
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
