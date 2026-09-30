package com.demandhub.platform.ai.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.demandhub.platform.config.AppProperties;
import java.util.stream.Collectors;

/**
 * Provedor Claude via SDK oficial. Modelo padrão: claude-opus-5-5 (thinking adaptativo é o padrão do modelo;
 * não se envia temperature — não suportado).
 */
public class AnthropicLlmClient implements LlmClient {

    public static final String DEFAULT_MODEL = "claude-opus-5-5";

    private final AnthropicClient client;
    private final String model;

    public AnthropicLlmClient(AppProperties.Ai props) {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new IllegalStateException("AI_API_KEY é obrigatório para AI_PROVIDER=anthropic.");
        }
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(props.apiKey())
                .timeout(props.timeout());
        if (props.baseUrl() != null && !props.baseUrl().isBlank()) {
            builder.baseUrl(props.baseUrl());
        }
        this.client = builder.build();
        this.model = props.model() == null || props.model().isBlank() ? DEFAULT_MODEL : props.model();
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(request.maxTokens())
                .system(request.system());
        for (LlmMessage m : request.messages()) {
            if ("assistant".equals(m.role())) {
                params.addAssistantMessage(m.content());
            } else {
                params.addUserMessage(m.content());
            }
        }
        Message response;
        try {
            response = client.messages().create(params.build());
        } catch (AnthropicServiceException e) {
            throw new LlmException("Falha no provedor Claude (HTTP " + e.statusCode() + ")", e);
        } catch (RuntimeException e) {
            throw new LlmException("Falha de comunicação com o provedor Claude", e);
        }
        String stopReason = response.stopReason().map(Object::toString).orElse("");
        if ("refusal".equalsIgnoreCase(stopReason)) {
            throw new LlmException("O provedor recusou a solicitação (stop_reason=refusal).");
        }
        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining());
        if (text.isBlank()) {
            throw new LlmException("Resposta vazia do provedor (stop_reason=" + stopReason + ").");
        }
        return new LlmResponse(text, provider(), model, false);
    }

    @Override
    public String provider() {
        return "anthropic";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public boolean isMock() {
        return false;
    }
}
