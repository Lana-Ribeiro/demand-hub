package com.demandhub.platform.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.demandhub.platform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/** E2E (API) — demanda não técnica: PMO → aprovação → execução operacional → conclusão, sem GitLab. */
class NonTechnicalDemandFlowTest extends IntegrationTest {

    @Test
    void resourceAllocationFollowsOperationalWorkflowWithoutGitLab() throws Exception {
        String client = userWithRoles("CLIENT");
        String pmo = userWithRoles("PMO");
        String manager = userWithRoles("MANAGER");
        project("SMARTDESK");

        Map<String, String> form = completeForm("RESOURCE_ALLOCATION", "SMARTDESK");
        form.put("title", "Alocação de dois analistas para o SmartDesk");
        String id = json(postJson("/api/demands", client, Map.of("fields", form))).path("summary").path("id").asText();
        assertThat(postJson("/api/demands/" + id + "/submit", client, Map.of()).getResponse().getStatus()).isEqualTo(200);

        JsonNode detail = getJson("/api/demands/" + id, pmo);
        assertThat(detail.path("workflowCode").asText()).isEqualTo("OPERATIONAL");
        assertThat(detail.path("summary").path("stage").path("code").asText()).isEqualTo("PMO_TRIAGE");

        assertThat(postJson("/api/demands/" + id + "/transitions", pmo, Map.of("action", "NOOP")).getResponse().getStatus())
                .as("ação inexistente no estágio é recusada").isEqualTo(422);
        mvcPatch(id, pmo, Map.of("priority", "P3"));
        assertThat(postJson("/api/demands/" + id + "/approve", pmo, Map.of()).getResponse().getStatus()).isEqualTo(200);
        assertThat(stage(id, pmo)).isEqualTo("CAPACITY_ANALYSIS");

        assertThat(postJson("/api/demands/" + id + "/transitions", pmo, Map.of("action", "ADVANCE")).getResponse().getStatus()).isEqualTo(200);
        assertThat(stage(id, pmo)).isEqualTo("MANAGER_APPROVAL");

        String approvalId = StreamSupport.stream(getJson("/api/approvals/pending", manager).spliterator(), false)
                .filter(p -> p.path("demand").path("id").asText().equals(id)).findFirst().orElseThrow().path("approval").path("id").asText();
        postJson("/api/approvals/" + approvalId + "/decision", manager, Map.of("approve", true, "comment", "Capacidade confirmada."));
        assertThat(stage(id, pmo)).isEqualTo("OPERATIONAL_EXECUTION");

        // Nenhuma execução técnica/GitLab para demanda não técnica
        JsonNode panel = getJson("/api/demands/" + id + "/execution", pmo);
        assertThat(panel.path("technicalDemand").asBoolean()).isFalse();
        assertThat(panel.path("technicalExecution").isNull() || panel.path("technicalExecution").isMissingNode()).isTrue();

        assertThat(postJson("/api/demands/" + id + "/transitions", pmo, Map.of("action", "COMPLETE")).getResponse().getStatus()).isEqualTo(200);
        assertThat(getJson("/api/demands/" + id, client).path("summary").path("lifecycleState").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void informationRequestPutsDemandOnHoldUntilRequesterAnswers() throws Exception {
        String client = userWithRoles("CLIENT");
        String pmo = userWithRoles("PMO");
        String id = json(postJson("/api/demands", client, Map.of("fields", completeForm("CONSULTING", null)))).path("summary").path("id").asText();
        postJson("/api/demands/" + id + "/submit", client, Map.of());

        var requestResult = postJson("/api/demands/" + id + "/request-information", pmo, Map.of("question", "Quantas pessoas usam o processo hoje?"));
        assertThat(requestResult.getResponse().getStatus()).isEqualTo(201);
        JsonNode request = json(requestResult);
        assertThat(stage(id, pmo)).isEqualTo("INFO_REQUESTED");
        assertThat(getJson("/api/demands/" + id, client).path("summary").path("lifecycleState").asText()).isEqualTo("ON_HOLD");

        postJson("/api/demands/" + id + "/information-requests/" + request.path("id").asText() + "/answer", client,
                Map.of("response", "Cerca de 120 pessoas."));
        assertThat(stage(id, pmo)).as("volta automaticamente para triagem").isEqualTo("PMO_TRIAGE");
    }

    @Test
    void rejectionRequiresReasonAndIsFinal() throws Exception {
        String client = userWithRoles("CLIENT");
        String pmo = userWithRoles("PMO");
        String id = json(postJson("/api/demands", client, Map.of("fields", completeForm("SUPPORT", null)))).path("summary").path("id").asText();
        postJson("/api/demands/" + id + "/submit", client, Map.of());

        assertThat(json(postJson("/api/demands/" + id + "/reject", pmo, Map.of())).path("code").asText()).isEqualTo("REASON_REQUIRED");
        assertThat(postJson("/api/demands/" + id + "/reject", pmo, Map.of("reason", "Fora do escopo da diretoria.")).getResponse().getStatus()).isEqualTo(200);
        JsonNode d = getJson("/api/demands/" + id, client);
        assertThat(d.path("summary").path("lifecycleState").asText()).isEqualTo("REJECTED");
        assertThat(d.path("availableTransitions").size()).isZero();
    }

    private void mvcPatch(String id, String token, Map<String, String> fields) throws Exception {
        mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/demands/" + id), token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(write(Map.of("fields", fields, "reason", "Classificação PMO"))));
    }

    private String stage(String id, String token) throws Exception {
        return getJson("/api/demands/" + id, token).path("summary").path("stage").path("code").asText();
    }
}
