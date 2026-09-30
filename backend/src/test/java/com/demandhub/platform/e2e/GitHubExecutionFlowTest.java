package com.demandhub.platform.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.demandhub.platform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** Execução técnica no GitHub (SCM_PROVIDER=github): issue criada na liberação e webhook assinado atualizando o status técnico. */
@TestPropertySource(properties = {
        "app.scm.provider=github",
        "app.github.mode=mock",
        "app.github.repository=acme/demand-hub-tests",
        "app.github.webhook-secret=test-github-webhook-secret"
})
class GitHubExecutionFlowTest extends IntegrationTest {

    private static final String SECRET = "test-github-webhook-secret";

    @Test
    void technicalDemandCreatesGitHubIssueAndSignedWebhookUpdatesExecution() throws Exception {
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
        postJson("/api/demands/" + id + "/refinement/items", pmo, Map.of("type", "ACCEPTANCE_CRITERION", "description", "Inventário atualizado em 1h"));
        assertThat(postJson("/api/demands/" + id + "/transitions", pmo, Map.of("action", "ADVANCE")).getResponse().getStatus()).isEqualTo(200);
        String approvalId = StreamSupport.stream(getJson("/api/approvals/pending", architect).spliterator(), false)
                .filter(p -> p.path("demand").path("id").asText().equals(id)).findFirst().orElseThrow().path("approval").path("id").asText();
        postJson("/api/approvals/" + approvalId + "/decision", architect, Map.of("approve", true));

        JsonNode panel = getJson("/api/demands/" + id + "/execution", pmo);
        assertThat(panel.path("scmSystem").asText()).isEqualTo("GitHub");
        JsonNode repo = panel.path("technicalExecution").path("repository");
        assertThat(repo.path("system").asText()).isEqualTo("GITHUB");
        assertThat(repo.path("mode").asText()).isEqualTo("MOCK");
        assertThat(repo.path("projectRef").asText()).isEqualTo("acme/demand-hub-tests");
        assertThat(panel.path("technicalExecution").path("system").asText()).isEqualTo("GitHub");

        String body = write(Map.of("action", "labeled",
                "repository", Map.of("full_name", "acme/demand-hub-tests"),
                "issue", Map.of("number", Integer.parseInt(repo.path("key").asText()), "state", "open",
                        "labels", List.of(Map.of("name", "demand-hub"), Map.of("name", "status::qa")))));

        // Assinatura inválida → 401
        mvc.perform(post("/api/integrations/github/webhook").header("X-GitHub-Event", "issues").header("X-Hub-Signature-256", "sha256=00")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());

        // Assinatura válida → status técnico QA; estágio da demanda não muda
        mvc.perform(post("/api/integrations/github/webhook").header("X-GitHub-Event", "issues").header("X-Hub-Signature-256", sign(body))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.processed").value(true));
        panel = getJson("/api/demands/" + id + "/execution", pmo);
        assertThat(panel.path("technicalExecution").path("status").asText()).isEqualTo("QA");
        assertThat(getJson("/api/demands/" + id, pmo).path("summary").path("stage").path("code").asText()).isEqualTo("READY_FOR_DEVELOPMENT");
    }

    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
