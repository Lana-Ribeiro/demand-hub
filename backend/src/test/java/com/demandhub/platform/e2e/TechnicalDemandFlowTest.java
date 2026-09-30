package com.demandhub.platform.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.demandhub.platform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/**
 * E2E (API) — demanda técnica:
 * cliente cria → anexa documento → conversa com IA → envia → IA analisa → PMO revisa → PMO aprova → Jira criado →
 * aprovação do diretor → refinamento → arquitetura aprovada → GitLab criado → execução → QA → documentação → conclusão.
 */
class TechnicalDemandFlowTest extends IntegrationTest {

    @Test
    void fullTechnicalLifecycle() throws Exception {
        String client = userWithRoles("CLIENT");
        String pmo = userWithRoles("PMO");
        String director = userWithRoles("DIRECTOR");
        String architect = userWithRoles("ARCHITECT");
        String developer = userWithRoles("DEVELOPER");
        project("AUTOREDE");

        // 1. Cliente cria rascunho (prazo no formulário: dezembro/2026)
        Map<String, String> form = completeForm("AUTOMATION", null);
        form.put("desiredDate", "2026-12-15");
        form.put("deadlineJustification", "Janela de manutenção anual.");
        MvcResult created = mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/demands"), client)
                .contentType(MediaType.APPLICATION_JSON).content(write(Map.of("fields", form))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.lifecycleState").value("DRAFT"))
                .andReturn();
        String id = json(created).path("summary").path("id").asText();

        // 2. Upload da apresentação com prazo divergente (outubro/2026) → sugestão + divergência
        MockMultipartFile ppt = new MockMultipartFile("file", "apresentacao.txt", "text/plain",
                "Objetivo: Automatizar o inventário de rede\nPrazo: Outubro/2026\nPatrocinador: Maria Patrocinadora\n".getBytes(StandardCharsets.UTF_8));
        mvc.perform(auth(multipart("/api/demands/" + id + "/documents").file(ppt).param("kind", "PRESENTATION"), client))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.extractionStatus").value("EXTRACTED"));
        JsonNode suggestions = getJson("/api/demands/" + id + "/suggestions?pending=true", client);
        assertThat(find(suggestions, "INCONSISTENCY", "desiredDate")).as("divergência formulário × apresentação").isNotNull();
        JsonNode divergence = find(suggestions, "INCONSISTENCY", "desiredDate");
        assertThat(divergence.path("suggestedValue").asText()).isEqualTo("2026-10-01");
        assertThat(divergence.path("alternativeValue").asText()).isEqualTo("2026-12-15");

        // Cliente decide manter o prazo do formulário (rejeita a divergência)
        postJson("/api/demands/" + id + "/suggestions/" + divergence.path("id").asText() + "/decision", client,
                Map.of("action", "REJECT", "reason", "Prazo do formulário é o vigente."));

        // 3. Chatbot responde e não aplica nada sem aceite
        MvcResult chat = postJson("/api/demands/" + id + "/chat", client, Map.of("message", "Quero criar uma iniciativa para automatizar o processo X."));
        assertThat(chat.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(chat).path("message").path("content").asText()).isNotBlank();
        assertThat(json(chat).path("mode").asText()).isEqualTo("MOCK");

        // 4. Envio → protocolo → análise IA (síncrona em teste) → Triagem PMO
        mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/demands/" + id + "/submit"), client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.protocol").isNotEmpty());
        JsonNode detail = getJson("/api/demands/" + id, pmo);
        assertThat(detail.path("summary").path("stage").path("code").asText()).isEqualTo("PMO_TRIAGE");
        JsonNode analysis = getJson("/api/demands/" + id + "/ai-analysis", pmo);
        assertThat(analysis.path("kind").asText()).isEqualTo("TRIAGE");
        assertThat(mapper.readTree(analysis.path("content").asText()).path("summary").path("executiveSummary").asText()).isNotBlank();

        // 5. PMO não consegue aprovar sem classificação completa (gate)
        MvcResult blocked = postJson("/api/demands/" + id + "/approve", pmo, Map.of());
        assertThat(blocked.getResponse().getStatus()).isEqualTo(422);
        assertThat(json(blocked).path("code").asText()).isEqualTo("GATE_BLOCKED");

        // 6. PMO classifica (P1 exige diretor) com motivo auditado e aprova
        mvc.perform(auth(patch("/api/demands/" + id), pmo).contentType(MediaType.APPLICATION_JSON)
                        .content(write(Map.of("fields", Map.of("project", "AUTOREDE", "priority", "P1"), "reason", "Impacto regulatório no inventário"))))
                .andExpect(status().isOk());
        mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/demands/" + id + "/approve"), pmo))
                .andExpect(status().isOk());
        detail = getJson("/api/demands/" + id, pmo);
        assertThat(detail.path("summary").path("stage").path("code").asText()).isEqualTo("EXECUTIVE_APPROVAL");
        assertThat(detail.path("summary").path("jira").path("mode").asText()).as("Jira criado em modo MOCK explícito").isEqualTo("MOCK");
        assertThat(detail.path("summary").path("jira").path("key").asText()).isNotBlank();

        // 7. Avanço bloqueado enquanto a aprovação do diretor está pendente
        MvcResult gate = postJson("/api/demands/" + id + "/transitions", pmo, Map.of("action", "ADVANCE"));
        assertThat(json(gate).path("code").asText()).isEqualTo("GATE_BLOCKED");

        // 8. Diretor aprova → auto-avanço para Refinamento
        JsonNode pending = getJson("/api/approvals/pending", director);
        String approvalId = StreamSupport.stream(pending.spliterator(), false)
                .filter(p -> p.path("demand").path("id").asText().equals(id)).findFirst().orElseThrow()
                .path("approval").path("id").asText();
        assertThat(postJson("/api/approvals/" + approvalId + "/decision", director, Map.of("approve", true, "comment", "De acordo.")).getResponse().getStatus()).isEqualTo(200);
        assertThat(stage(id, pmo)).isEqualTo("REFINEMENT");

        // 9. Refinamento: gate exige requisitos e critérios
        assertThat(json(postJson("/api/demands/" + id + "/transitions", pmo, Map.of("action", "ADVANCE"))).path("code").asText()).isEqualTo("GATE_BLOCKED");
        JsonNode meeting = json(postJson("/api/demands/" + id + "/refinement/meetings", pmo, Map.of("title", "Refinamento 1",
                "participants", "PMO, Arquitetura, Negócio",
                "notes", "- O sistema deve importar os equipamentos da gerência a cada hora\n- Critério de aceite: inventário atualizado em até 1 hora\n- Ficou decidido usar API REST da gerência\n- Qual o volume de equipamentos?")));
        JsonNode aiItems = json(postJson("/api/demands/" + id + "/refinement/meetings/" + meeting.path("id").asText() + "/ai-suggestions", pmo, Map.of()));
        assertThat(aiItems.size()).isGreaterThanOrEqualTo(3);
        // Aceita as sugestões do Refinement Agent, exceto a dúvida (que seria bloqueante)
        for (JsonNode s : aiItems) {
            String action = "QUESTION".equals(s.path("field").asText()) ? "REJECT" : "ACCEPT";
            assertThat(postJson("/api/demands/" + id + "/suggestions/" + s.path("id").asText() + "/decision", pmo,
                    Map.of("action", action, "reason", "Revisado no refinamento")).getResponse().getStatus()).isEqualTo(200);
        }
        assertThat(postJson("/api/demands/" + id + "/transitions", pmo, Map.of("action", "ADVANCE")).getResponse().getStatus()).isEqualTo(200);
        assertThat(stage(id, pmo)).isEqualTo("ARCHITECTURE_REVIEW");

        // 10. Arquitetura: agente apoia, arquiteto humano aprova → READY + GitLab criado
        assertThat(postJson("/api/demands/" + id + "/artifacts/ARCHITECTURE", architect, Map.of()).getResponse().getStatus()).isEqualTo(200);
        String archApproval = StreamSupport.stream(getJson("/api/approvals/pending", architect).spliterator(), false)
                .filter(p -> p.path("demand").path("id").asText().equals(id)).findFirst().orElseThrow().path("approval").path("id").asText();
        postJson("/api/approvals/" + archApproval + "/decision", architect, Map.of("approve", true, "comment", "Arquitetura aprovada."));
        assertThat(stage(id, pmo)).isEqualTo("READY_FOR_DEVELOPMENT");
        JsonNode panel = getJson("/api/demands/" + id + "/execution", pmo);
        assertThat(panel.path("technicalExecution").path("status").asText()).isEqualTo("TODO");
        assertThat(panel.path("technicalExecution").path("repository").path("mode").asText()).isEqualTo("MOCK");

        // 11. Prompt técnico (Estratégia A) e início da execução
        JsonNode prompt = json(postJson("/api/demands/" + id + "/artifacts/TECH_PROMPT", architect, Map.of()));
        assertThat(mapper.readTree(prompt.path("content").asText()).path("markdown").asText()).contains("Critérios de aceite");
        assertThat(postJson("/api/demands/" + id + "/transitions", developer, Map.of("action", "START_EXECUTION")).getResponse().getStatus()).isEqualTo(200);
        assertThat(stage(id, pmo)).isEqualTo("IN_DEVELOPMENT");
        assertThat(json(postJson("/api/demands/" + id + "/transitions", pmo, Map.of("action", "ADVANCE"))).path("code").asText()).isEqualTo("GATE_BLOCKED");

        // 12. GitLab → QA: execução avança, estágio da demanda NÃO muda (Jira = lifecycle da demanda)
        postJson("/api/integrations/scm/mock/executions/" + id + "/status", developer, Map.of("status", "QA"));
        panel = getJson("/api/demands/" + id + "/execution", pmo);
        assertThat(panel.path("technicalExecution").path("status").asText()).isEqualTo("QA");
        assertThat(panel.path("demandLifecycle").path("stage").asText()).isEqualTo("Em desenvolvimento");
        JsonNode audit = getJson("/api/demands/" + id + "/audit", pmo);
        assertThat(StreamSupport.stream(audit.spliterator(), false).anyMatch(a -> a.path("action").asText().equals("JIRA_COMMENT_ADDED")
                && a.path("metadata").asText().contains("QA"))).as("comentário no Jira sobre QA").isTrue();

        // 13. Webhook do GitLab (caminho real, com segredo) → DONE libera o gate
        JsonNode gitlabLink = panel.path("technicalExecution").path("repository");
        String hook = write(Map.of("object_kind", "issue",
                "project", Map.of("id", gitlabLink.path("projectRef").asText()),
                "object_attributes", Map.of("iid", gitlabLink.path("key").asText(), "state", "opened"),
                "labels", List.of(Map.of("title", "demand-hub"), Map.of("title", "status::done"))));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/integrations/gitlab/webhook")
                        .header("X-Gitlab-Token", "test-gitlab-webhook-token").contentType(MediaType.APPLICATION_JSON).content(hook))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(true));
        assertThat(getJson("/api/demands/" + id + "/execution", pmo).path("technicalExecution").path("status").asText()).isEqualTo("DONE");

        // Webhook do Jira: comentário feito no Jira aparece na demanda (interno); eco da plataforma é ignorado
        String jiraKey = getJson("/api/demands/" + id, pmo).path("summary").path("jira").path("key").asText();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/integrations/jira/webhook?token=test-jira-webhook-token")
                        .contentType(MediaType.APPLICATION_JSON).content(write(Map.of("webhookEvent", "comment_created",
                                "issue", Map.of("key", jiraKey),
                                "comment", Map.of("id", "10001", "body", "Validado com a área de negócio.", "author", Map.of("displayName", "Pessoa no Jira"))))))
                .andExpect(status().isOk());
        JsonNode comments = getJson("/api/demands/" + id + "/comments", pmo);
        assertThat(StreamSupport.stream(comments.spliterator(), false).anyMatch(c -> "JIRA".equals(c.path("source").asText()))).isTrue();
        assertThat(getJson("/api/demands/" + id + "/comments", client).size()).as("comentário interno não visível ao cliente").isZero();

        // Documentação obrigatória antes de concluir
        assertThat(postJson("/api/demands/" + id + "/transitions", pmo, Map.of("action", "ADVANCE")).getResponse().getStatus()).isEqualTo(200);
        assertThat(stage(id, pmo)).isEqualTo("DOCUMENTATION");
        assertThat(json(postJson("/api/demands/" + id + "/transitions", pmo, Map.of("action", "COMPLETE"))).path("code").asText()).isEqualTo("GATE_BLOCKED");
        postJson("/api/demands/" + id + "/artifacts/TECHNICAL_DOC", pmo, Map.of());
        postJson("/api/demands/" + id + "/artifacts/CLIENT_DOC", pmo, Map.of());
        assertThat(postJson("/api/demands/" + id + "/transitions", pmo, Map.of("action", "COMPLETE")).getResponse().getStatus()).isEqualTo(200);

        detail = getJson("/api/demands/" + id, client);
        assertThat(detail.path("summary").path("lifecycleState").asText()).isEqualTo("COMPLETED");
        assertThat(detail.path("summary").path("readOnly").asBoolean()).isTrue();

        // Auditoria: mudança de prioridade com antes/depois/motivo e decisões sobre sugestões da IA
        audit = getJson("/api/demands/" + id + "/audit", pmo);
        JsonNode priorityChange = StreamSupport.stream(audit.spliterator(), false)
                .filter(a -> a.path("action").asText().equals("CHANGE_PRIORITY")).findFirst().orElseThrow();
        assertThat(priorityChange.path("afterValue").asText()).isEqualTo("P1");
        assertThat(priorityChange.path("reason").asText()).isEqualTo("Impacto regulatório no inventário");
        assertThat(StreamSupport.stream(audit.spliterator(), false).filter(a -> a.path("action").asText().equals("AI_SUGGESTION_DECIDED")).count())
                .isGreaterThanOrEqualTo(3);

        // Documentação gerada disponível
        JsonNode docs = getJson("/api/demands/" + id + "/documents", client);
        List<String> kinds = StreamSupport.stream(docs.spliterator(), false).map(d -> d.path("kind").asText()).toList();
        assertThat(kinds).contains("TECHNICAL_DOC", "CLIENT_DOC", "PRESENTATION");

        // Demanda concluída é somente leitura
        mvc.perform(auth(put("/api/demands/" + id), client).contentType(MediaType.APPLICATION_JSON).content(write(Map.of("fields", form))))
                .andExpect(status().is4xxClientError());
    }

    private String stage(String id, String token) throws Exception {
        return getJson("/api/demands/" + id, token).path("summary").path("stage").path("code").asText();
    }

    private static JsonNode find(JsonNode suggestions, String kind, String field) {
        for (JsonNode s : suggestions) {
            if (kind.equals(s.path("kind").asText()) && field.equals(s.path("field").asText())) {
                return s;
            }
        }
        return null;
    }
}
