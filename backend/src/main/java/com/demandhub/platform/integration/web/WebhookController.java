package com.demandhub.platform.integration.web;

import com.demandhub.platform.shared.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Webhooks públicos (sem JWT) autenticados por segredo compartilhado ou assinatura HMAC. */
@RestController
@RequestMapping("/api/integrations")
public class WebhookController {

    private final WebhookService service;
    private final ObjectMapper mapper;

    public WebhookController(WebhookService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping("/gitlab/webhook")
    public Map<String, Object> gitlab(@RequestHeader(value = "X-Gitlab-Token", required = false) String token,
                                      @RequestBody JsonNode payload) {
        return Map.of("processed", service.handleGitLab(token, payload));
    }

    /** O corpo é recebido bruto: a assinatura HMAC é calculada sobre os bytes exatamente como enviados pelo GitHub. */
    @PostMapping("/github/webhook")
    public Map<String, Object> github(@RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
                                      @RequestHeader(value = "X-GitHub-Event", required = false) String event,
                                      @RequestBody String rawBody) {
        JsonNode payload;
        try {
            payload = mapper.readTree(rawBody);
        } catch (Exception e) {
            throw ApiException.badRequest("MALFORMED_REQUEST", "Payload inválido.");
        }
        return Map.of("processed", service.handleGitHub(signature, event == null ? "" : event, rawBody, payload));
    }

    @PostMapping("/jira/webhook")
    public Map<String, Object> jira(@RequestParam(value = "token", required = false) String token,
                                    @RequestBody JsonNode payload) {
        return Map.of("processed", service.handleJira(token, payload));
    }
}
