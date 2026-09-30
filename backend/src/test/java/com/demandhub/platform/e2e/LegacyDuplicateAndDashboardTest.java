package com.demandhub.platform.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.demandhub.platform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class LegacyDuplicateAndDashboardTest extends IntegrationTest {

    @Test
    void legacyIsReadOnlyWithoutInventedHistoryAndFeedsDuplicateDetection() throws Exception {
        String admin = userWithRoles("ADMIN");
        String client = userWithRoles("CLIENT");
        String pmo = userWithRoles("PMO");
        String originalId = "LEG-" + UUID.randomUUID().toString().substring(0, 8);
        String legacyTitle = "Sistema de monitoramento de energia das estações rádio base";

        JsonNode result = json(postJson("/api/admin/legacy/import", admin, Map.of("records", List.of(
                Map.of("originalId", originalId, "title", legacyTitle, "objective", "Monitorar consumo de energia das estações rádio base",
                        "originalStatus", "Em análise")))));
        assertThat(result.path("imported").asInt()).isEqualTo(1);
        // Reimportação é ignorada (idempotente)
        assertThat(json(postJson("/api/admin/legacy/import", admin, Map.of("records", List.of(
                Map.of("originalId", originalId, "title", legacyTitle))))).path("skipped").asInt()).isEqualTo(1);

        JsonNode legacyPage = getJson("/api/demands?includeLegacy=true&q=" + originalId, pmo);
        JsonNode legacy = legacyPage.path("content").get(0);
        assertThat(legacy.path("lifecycleState").asText()).isEqualTo("LEGACY");
        assertThat(legacy.path("readOnly").asBoolean()).isTrue();
        assertThat(legacy.path("stage").isNull() || legacy.path("stage").isMissingNode()).isTrue();
        JsonNode detail = getJson("/api/demands/" + legacy.path("id").asText(), pmo);
        assertThat(detail.path("stageHistory").size()).as("sem histórico inventado").isZero();

        // Nova demanda semelhante → duplicidade sugerida na triagem
        Map<String, String> form = completeForm("DEVELOPMENT", null);
        form.put("title", "Monitoramento de energia das estações rádio base");
        form.put("objective", "Monitorar o consumo de energia das estações rádio base em tempo real");
        String id = json(postJson("/api/demands", client, Map.of("fields", form))).path("summary").path("id").asText();
        postJson("/api/demands/" + id + "/submit", client, Map.of());
        JsonNode suggestions = getJson("/api/demands/" + id + "/suggestions?pending=true", pmo);
        assertThat(StreamSupport.stream(suggestions.spliterator(), false)
                .anyMatch(s -> "DUPLICATE".equals(s.path("kind").asText()) && s.path("suggestedValue").asText().contains(originalId))).isTrue();
    }

    @Test
    void dashboardReportsInsufficientDataInsteadOfFakeMetrics() throws Exception {
        String manager = userWithRoles("MANAGER");
        JsonNode dash = getJson("/api/dashboard?projectId=999999", manager);
        assertThat(dash.path("openDemands").asLong()).isZero();
        for (JsonNode m : dash.path("averageTimes")) {
            assertThat(m.path("sufficient").asBoolean()).isFalse();
            assertThat(m.path("averageHours").isNull() || m.path("averageHours").isMissingNode()).isTrue();
        }
        assertThat(dash.path("volumeByMonth").size()).isEqualTo(12);
    }
}
