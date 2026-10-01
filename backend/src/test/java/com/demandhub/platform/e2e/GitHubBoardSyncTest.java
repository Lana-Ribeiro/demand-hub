package com.demandhub.platform.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.demandhub.platform.execution.service.BoardSyncJob;
import com.demandhub.platform.integration.common.ExternalLink;
import com.demandhub.platform.integration.common.IntegrationConfig.IntegrationGateways;
import com.demandhub.platform.integration.common.IntegrationMode;
import com.demandhub.platform.integration.jira.JiraMockGateway;
import com.demandhub.platform.integration.scm.ScmGateway;
import com.demandhub.platform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;

@org.springframework.test.context.TestPropertySource(properties = "app.github.repository=acme/board-tests")
/** Board do GitHub Projects: card criado na coluna inicial; mover o card no board atualiza a execução técnica. */
class GitHubBoardSyncTest extends IntegrationTest {

    /** GitHub com board, em memória (sem rede). */
    static class FakeBoardGitHub implements ScmGateway {
        final Map<String, String> columns = new ConcurrentHashMap<>();
        final Map<String, String> labels = new ConcurrentHashMap<>();
        int sequence = 100;

        @Override public ExternalLink.System system() { return ExternalLink.System.GITHUB; }
        @Override public IntegrationMode mode() { return IntegrationMode.REAL; }
        @Override public synchronized CreatedIssue createIssue(String repo, String title, String description, List<String> l) {
            String key = String.valueOf(++sequence);
            labels.put(key, l.get(1));
            return new CreatedIssue("NODE_" + key, key, "https://github.com/" + repo + "/issues/" + key);
        }
        @Override public void addNote(String repo, String key, String body) {}
        @Override public void replaceStatusLabel(String repo, String key, String oldLabel, String newLabel) { labels.put(key, newLabel); }
        @Override public boolean hasBoard() { return true; }
        @Override public String addToBoard(CreatedIssue issue, String column) { columns.put("ITEM_" + issue.key(), column); return "ITEM_" + issue.key(); }
        @Override public void moveOnBoard(String itemId, String column) { columns.put(itemId, column); }
        @Override public Map<String, String> readBoardColumns(Collection<String> ids) {
            Map<String, String> r = new HashMap<>();
            ids.forEach(id -> { if (columns.containsKey(id)) r.put(id, columns.get(id)); });
            return r;
        }
    }

    static final FakeBoardGitHub GITHUB = new FakeBoardGitHub();

    @TestConfiguration
    static class BoardConfig {
        @Bean
        @Primary
        IntegrationGateways boardGateways() {
            return new IntegrationGateways(new JiraMockGateway(), GITHUB, ExternalLink.System.GITHUB);
        }
    }

    @Autowired BoardSyncJob boardSync;

    @Test
    void cardCreatedOnBoardAndBoardMovesUpdateExecution() throws Exception {
        String client = userWithRoles("CLIENT");
        String pmo = userWithRoles("PMO");
        String architect = userWithRoles("ARCHITECT");
        project("AUTOREDE");

        String id = json(postJson("/api/demands", client, Map.of("fields", completeForm("DEVELOPMENT", "AUTOREDE")))).path("summary").path("id").asText();
        postJson("/api/demands/" + id + "/submit", client, Map.of());
        mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/demands/" + id), pmo)
                .contentType(MediaType.APPLICATION_JSON).content(write(Map.of("fields", Map.of("priority", "P3"), "reason", "Triagem"))));
        postJson("/api/demands/" + id + "/approve", pmo, Map.of());
        postJson("/api/demands/" + id + "/refinement/items", pmo, Map.of("type", "FUNCTIONAL_REQUIREMENT", "description", "Importar equipamentos"));
        postJson("/api/demands/" + id + "/refinement/items", pmo, Map.of("type", "ACCEPTANCE_CRITERION", "description", "Atualizado em 1h"));
        postJson("/api/demands/" + id + "/transitions", pmo, Map.of("action", "ADVANCE"));
        String approvalId = StreamSupport.stream(getJson("/api/approvals/pending", architect).spliterator(), false)
                .filter(p -> p.path("demand").path("id").asText().equals(id)).findFirst().orElseThrow().path("approval").path("id").asText();
        postJson("/api/approvals/" + approvalId + "/decision", architect, Map.of("approve", true));

        JsonNode repo = getJson("/api/demands/" + id + "/execution", pmo).path("technicalExecution").path("repository");
        String key = repo.path("key").asText();
        assertThat(GITHUB.columns.get("ITEM_" + key)).as("card criado na coluna inicial").isEqualTo("A fazer");

        // Alguém move o card no board para QA → próxima leitura aplica o status técnico
        GITHUB.columns.put("ITEM_" + key, "QA");
        boardSync.sync();

        JsonNode panel = getJson("/api/demands/" + id + "/execution", pmo);
        assertThat(panel.path("technicalExecution").path("status").asText()).isEqualTo("QA");
        assertThat(GITHUB.labels.get(key)).as("label espelhada").isEqualTo("status::qa");
        assertThat(getJson("/api/demands/" + id, pmo).path("summary").path("stage").path("code").asText())
                .as("etapa da demanda não muda com o avanço técnico").isEqualTo("READY_FOR_DEVELOPMENT");
        JsonNode audit = getJson("/api/demands/" + id + "/audit", pmo);
        assertThat(StreamSupport.stream(audit.spliterator(), false).anyMatch(a -> a.path("action").asText().equals("EXECUTION_STATUS_CHANGED")
                && a.path("metadata").asText().contains("GITHUB_BOARD"))).isTrue();

        // Mudança feita na plataforma (registro manual não se aplica aqui) — leitura repetida não gera novo evento
        boardSync.sync();
        long changes = StreamSupport.stream(getJson("/api/demands/" + id + "/audit", pmo).spliterator(), false)
                .filter(a -> a.path("action").asText().equals("EXECUTION_STATUS_CHANGED")).count();
        assertThat(changes).isEqualTo(1);
    }
}
