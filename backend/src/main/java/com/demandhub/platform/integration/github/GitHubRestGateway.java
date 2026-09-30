package com.demandhub.platform.integration.github;

import com.demandhub.platform.config.AppProperties;
import com.demandhub.platform.integration.common.ExternalLink;
import com.demandhub.platform.integration.common.IntegrationMode;
import com.demandhub.platform.integration.scm.ScmGateway;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Integração REAL com GitHub (REST API v3 — Issues). Autenticação por token (fine-grained com permissão Issues: read/write,
 * ou clássico com escopo repo). Labels inexistentes são criadas pelo próprio GitHub ao serem aplicadas na issue.
 */
public class GitHubRestGateway implements ScmGateway {

    private static final Pattern REPOSITORY = Pattern.compile("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$");

    private final RestClient http;

    public GitHubRestGateway(AppProperties.Github props) {
        if (props.token() == null || props.token().isBlank()) {
            throw new IllegalStateException("GITHUB_TOKEN é obrigatório para GITHUB_MODE=real.");
        }
        String api = props.apiUrl() == null || props.apiUrl().isBlank() ? "https://api.github.com" : props.apiUrl().replaceAll("/+$", "");
        this.http = RestClient.builder().baseUrl(api)
                .defaultHeader("Authorization", "Bearer " + props.token())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    @Override
    public ExternalLink.System system() {
        return ExternalLink.System.GITHUB;
    }

    @Override
    public IntegrationMode mode() {
        return IntegrationMode.REAL;
    }

    @Override
    public CreatedIssue createIssue(String repository, String title, String description, List<String> labels) {
        String[] repo = split(repository);
        try {
            JsonNode res = http.post().uri("/repos/{owner}/{repo}/issues", repo[0], repo[1]).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("title", title, "body", description, "labels", labels))
                    .retrieve().body(JsonNode.class);
            return new CreatedIssue(res.path("id").asText(), res.path("number").asText(), res.path("html_url").asText(null));
        } catch (RestClientException e) {
            throw new ScmException("Falha ao criar issue no GitHub: " + e.getMessage(), e);
        }
    }

    @Override
    public void addNote(String repository, String issueNumber, String body) {
        String[] repo = split(repository);
        try {
            http.post().uri("/repos/{owner}/{repo}/issues/{number}/comments", repo[0], repo[1], issueNumber)
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of("body", body)).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new ScmException("Falha ao comentar no GitHub: " + e.getMessage(), e);
        }
    }

    private static String[] split(String repository) {
        if (repository == null || !REPOSITORY.matcher(repository).matches()) {
            throw new ScmException("Repositório GitHub inválido (use owner/repositorio): " + repository, null);
        }
        return repository.split("/");
    }
}
