package com.demandhub.platform.integration.github;

import com.demandhub.platform.integration.scm.ScmGateway.ScmException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Board do GitHub Projects (v2) via GraphQL: adiciona issues, move cards entre colunas (campo "Status")
 * e lê a coluna atual dos itens. Projeto e campo são resolvidos uma vez e mantidos em cache.
 */
public class GitHubProjectsClient {

    private final RestClient http;
    private final String owner;
    private final int number;
    private volatile Board board;

    record Board(String projectId, String statusFieldId, Map<String, String> optionIdsByName) {}

    public GitHubProjectsClient(RestClient http, String owner, int number) {
        this.http = http;
        this.owner = owner;
        this.number = number;
    }

    public String addItem(String contentNodeId, String columnName) {
        Board b = board();
        JsonNode data = graphql("""
                mutation($project: ID!, $content: ID!) {
                  addProjectV2ItemById(input: {projectId: $project, contentId: $content}) { item { id } }
                }""", Map.of("project", b.projectId(), "content", contentNodeId));
        String itemId = data.path("addProjectV2ItemById").path("item").path("id").asText(null);
        if (itemId == null) {
            throw new ScmException("GitHub Projects não retornou o item adicionado.", null);
        }
        move(itemId, columnName);
        return itemId;
    }

    public void move(String itemId, String columnName) {
        Board b = board();
        String optionId = b.optionIdsByName().get(columnName.toLowerCase(Locale.ROOT));
        if (optionId == null) {
            throw new ScmException("Coluna \"" + columnName + "\" não existe no board (campo Status). Colunas: "
                    + b.optionIdsByName().keySet(), null);
        }
        graphql("""
                mutation($project: ID!, $item: ID!, $field: ID!, $option: String!) {
                  updateProjectV2ItemFieldValue(input: {projectId: $project, itemId: $item, fieldId: $field,
                    value: {singleSelectOptionId: $option}}) { projectV2Item { id } }
                }""", Map.of("project", b.projectId(), "item", itemId, "field", b.statusFieldId(), "option", optionId));
    }

    public Map<String, String> readColumns(Collection<String> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        JsonNode data = graphql("""
                query($ids: [ID!]!) {
                  nodes(ids: $ids) {
                    ... on ProjectV2Item { id fieldValueByName(name: "Status") { ... on ProjectV2ItemFieldSingleSelectValue { name } } }
                  }
                }""", Map.of("ids", List.copyOf(itemIds)));
        Map<String, String> result = new HashMap<>();
        for (JsonNode n : data.path("nodes")) {
            String name = n.path("fieldValueByName").path("name").asText(null);
            if (n.hasNonNull("id") && name != null) {
                result.put(n.get("id").asText(), name);
            }
        }
        return result;
    }

    private Board board() {
        Board b = board;
        if (b == null) {
            JsonNode data = graphql("""
                    query($owner: String!, $number: Int!) {
                      user(login: $owner) { projectV2(number: $number) { id
                        field(name: "Status") { ... on ProjectV2SingleSelectField { id options { id name } } } } }
                    }""", Map.of("owner", owner, "number", number));
            JsonNode project = data.path("user").path("projectV2");
            if (project.isMissingNode() || project.isNull()) {
                throw new ScmException("Board " + owner + "/projects/" + number + " não encontrado ou sem acesso.", null);
            }
            Map<String, String> options = new LinkedHashMap<>();
            project.path("field").path("options").forEach(o -> options.put(o.path("name").asText().toLowerCase(Locale.ROOT), o.path("id").asText()));
            b = new Board(project.path("id").asText(), project.path("field").path("id").asText(), options);
            board = b;
        }
        return b;
    }

    private JsonNode graphql(String query, Map<String, Object> variables) {
        try {
            JsonNode res = http.post().uri("/graphql").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", query, "variables", variables)).retrieve().body(JsonNode.class);
            if (res == null) {
                throw new ScmException("Resposta vazia do GitHub GraphQL.", null);
            }
            if (res.has("errors") && res.get("errors").size() > 0) {
                StringBuilder msg = new StringBuilder();
                res.get("errors").forEach(e -> msg.append(e.path("message").asText()).append("; "));
                throw new ScmException("GitHub Projects: " + msg, null);
            }
            return res.path("data");
        } catch (RestClientException e) {
            throw new ScmException("Falha ao acessar o GitHub Projects: " + e.getMessage(), e);
        }
    }
}
