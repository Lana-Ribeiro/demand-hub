package com.demandhub.platform.integration.gitlab;

import com.demandhub.platform.config.AppProperties;
import com.demandhub.platform.integration.common.ExternalLink;
import com.demandhub.platform.integration.common.IntegrationMode;
import com.demandhub.platform.integration.scm.ScmGateway;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Integração REAL com GitLab (REST API v4), autenticação por PRIVATE-TOKEN. */
public class GitLabRestGateway implements ScmGateway {

    private final RestClient http;

    public GitLabRestGateway(AppProperties.Gitlab props) {
        if (props.url() == null || props.url().isBlank() || props.token() == null || props.token().isBlank()) {
            throw new IllegalStateException("GITLAB_URL e GITLAB_TOKEN são obrigatórios para GITLAB_MODE=real.");
        }
        this.http = RestClient.builder().baseUrl(props.url().replaceAll("/+$", "") + "/api/v4")
                .defaultHeader("PRIVATE-TOKEN", props.token()).build();
    }

    @Override
    public ExternalLink.System system() {
        return ExternalLink.System.GITLAB;
    }

    @Override
    public IntegrationMode mode() {
        return IntegrationMode.REAL;
    }

    @Override
    public CreatedIssue createIssue(String projectId, String title, String description, List<String> labels) {
        try {
            JsonNode res = http.post().uri("/projects/{id}/issues", projectId).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("title", title, "description", description, "labels", String.join(",", labels)))
                    .retrieve().body(JsonNode.class);
            return new CreatedIssue(res.path("id").asText(), res.path("iid").asText(), res.path("web_url").asText(null));
        } catch (RestClientException e) {
            throw new ScmException("Falha ao criar issue no GitLab: " + e.getMessage(), e);
        }
    }

    @Override
    public void addNote(String projectId, String issueIid, String body) {
        try {
            http.post().uri("/projects/{id}/issues/{iid}/notes", projectId, issueIid).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("body", body)).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new ScmException("Falha ao comentar no GitLab: " + e.getMessage(), e);
        }
    }
}
