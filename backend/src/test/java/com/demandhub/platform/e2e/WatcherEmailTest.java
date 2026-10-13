package com.demandhub.platform.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.demandhub.platform.support.IntegrationTest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;

/** Acompanhantes (MAIL_WATCHERS) recebem e-mail de toda demanda nova e de toda mudança de etapa, com o que mudou. */
@TestPropertySource(properties = {
        "app.notifications.email.enabled=true",
        "app.notifications.email.from=demandhub@empresa.com",
        "app.notifications.email.watchers=gestora@empresa.com"
})
class WatcherEmailTest extends IntegrationTest {

    @MockBean JavaMailSender mailSender;

    @Test
    void watcherReceivesNewDemandAndEveryStageChange() throws Exception {
        String client = userWithRoles("CLIENT");
        String pmo = userWithRoles("PMO");
        project("AUTOREDE");

        String id = json(postJson("/api/demands", client, Map.of("fields", completeForm("DEVELOPMENT", "AUTOREDE")))).path("summary").path("id").asText();
        postJson("/api/demands/" + id + "/submit", client, Map.of());
        mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/demands/" + id), pmo)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(write(Map.of("fields", Map.of("priority", "P3"), "reason", "Triagem"))));
        postJson("/api/demands/" + id + "/approve", pmo, Map.of());

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(sent.capture());
        List<SimpleMailMessage> toWatcher = sent.getAllValues().stream()
                .filter(m -> m.getTo() != null && Arrays.asList(m.getTo()).contains("gestora@empresa.com")).toList();
        List<String> subjects = toWatcher.stream().map(SimpleMailMessage::getSubject).toList();

        assertThat(subjects).anyMatch(s -> s.startsWith("[Demand Hub] Nova demanda DEM-"));
        assertThat(subjects).as("análise da IA concluída").anyMatch(s -> s.contains("Análise da IA → Triagem PMO"));
        assertThat(subjects).as("aprovação do PMO").anyMatch(s -> s.contains("Triagem PMO → "));
        SimpleMailMessage approval = toWatcher.stream().filter(m -> m.getSubject().contains("Triagem PMO → ")).findFirst().orElseThrow();
        assertThat(approval.getText()).contains("O que mudou:", "Etapa: Triagem PMO → ", "Ação: APPROVE", "Projeto: AUTOREDE", "Prioridade: P3", "/demands/" + id);
        assertThat(approval.getFrom()).isEqualTo("demandhub@empresa.com");
    }
}
