package com.demandhub.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.demandhub.platform.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

class SecurityAndPermissionsTest extends IntegrationTest {

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/demands")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer token-invalido")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidCredentialsReturnGenericError() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(write(Map.of("email", "ninguem@test.local", "password", "qualquer"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientCannotSeeOthersDemandsNorActAsPmo() throws Exception {
        String owner = userWithRoles("CLIENT");
        String other = userWithRoles("CLIENT");
        String id = json(postJson("/api/demands", owner, Map.of("fields", completeForm("DEVELOPMENT", null)))).path("summary").path("id").asText();

        mvc.perform(auth(get("/api/demands/" + id), other)).andExpect(status().isNotFound());
        assertThat(getJson("/api/demands", other).path("content").size()).isZero();

        postJson("/api/demands/" + id + "/submit", owner, Map.of());
        assertThat(postJson("/api/demands/" + id + "/approve", owner, Map.of()).getResponse().getStatus()).isEqualTo(403);
        assertThat(postJson("/api/demands/" + id + "/transitions", owner, Map.of("action", "APPROVE")).getResponse().getStatus()).isEqualTo(403);
        mvc.perform(auth(get("/api/dashboard"), owner)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/audit"), owner)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/admin/users"), owner)).andExpect(status().isForbidden());
    }

    @Test
    void systemOnlyTransitionsCannotBeTriggeredByUsers() throws Exception {
        String client = userWithRoles("CLIENT");
        String pmo = userWithRoles("PMO");
        String id = json(postJson("/api/demands", client, Map.of("fields", completeForm("DEVELOPMENT", null)))).path("summary").path("id").asText();
        postJson("/api/demands/" + id + "/submit", client, Map.of());
        // Já em PMO_TRIAGE; ANALYSIS_DONE não existe mais aqui e, onde existe, é systemOnly.
        assertThat(postJson("/api/demands/" + id + "/transitions", pmo, Map.of("action", "ANALYSIS_DONE")).getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void submissionValidatesRequiredAndConditionalFields() throws Exception {
        String client = userWithRoles("CLIENT");
        Map<String, String> form = completeForm("DEVELOPMENT", null);
        form.put("hasBudgetImpact", "true");
        form.put("regulatoryRequirement", "true");
        form.remove("sponsorName");
        String id = json(postJson("/api/demands", client, Map.of("fields", form))).path("summary").path("id").asText();
        var res = json(postJson("/api/demands/" + id + "/submit", client, Map.of()));
        assertThat(res.path("code").asText()).isEqualTo("SUBMISSION_INCOMPLETE");
        assertThat(res.path("fieldErrors").has("sponsorName")).isTrue();
        assertThat(res.path("fieldErrors").has("estimatedBudget")).isTrue();
        assertThat(res.path("fieldErrors").has("costCenter")).isTrue();
        assertThat(res.path("fieldErrors").has("regulatoryDescription")).isTrue();
    }

    @Test
    void clientCannotSetPriorityOnDraft() throws Exception {
        String client = userWithRoles("CLIENT");
        Map<String, String> form = completeForm("DEVELOPMENT", null);
        String id = json(postJson("/api/demands", client, Map.of("fields", form))).path("summary").path("id").asText();
        form.put("priority", "P1");
        var res = mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/demands/" + id), client)
                .contentType(MediaType.APPLICATION_JSON).content(write(Map.of("fields", form)))).andReturn();
        // Campo de classificação é ignorado no formulário do cliente (não editável)
        var priority = json(res).path("fields").path("priority");
        assertThat(priority.isNull() || priority.isMissingNode()).isTrue();
    }

    @Test
    void uploadRejectsDisallowedOrSpoofedFiles() throws Exception {
        String client = userWithRoles("CLIENT");
        String id = json(postJson("/api/demands", client, Map.of("fields", completeForm("DEVELOPMENT", null)))).path("summary").path("id").asText();
        MockMultipartFile exe = new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[]{0x4D, 0x5A, 0x00});
        mvc.perform(auth(multipart("/api/demands/" + id + "/documents").file(exe), client)).andExpect(status().isBadRequest());
        MockMultipartFile fakePdf = new MockMultipartFile("file", "falso.pdf", "application/pdf", "apenas texto".getBytes());
        mvc.perform(auth(multipart("/api/demands/" + id + "/documents").file(fakePdf), client)).andExpect(status().isBadRequest());
    }

    @Test
    void webhooksRequireSharedSecret() throws Exception {
        mvc.perform(post("/api/integrations/gitlab/webhook").contentType(MediaType.APPLICATION_JSON).content("{\"object_kind\":\"issue\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/integrations/gitlab/webhook").header("X-Gitlab-Token", "errado")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"object_kind\":\"issue\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/integrations/gitlab/webhook").header("X-Gitlab-Token", "test-gitlab-webhook-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"object_kind\":\"push\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/integrations/jira/webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
