package com.demandhub.platform.ai.llm;

import com.demandhub.platform.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Provedores com API de Chat Completions: OpenAI ({@code AI_PROVIDER=openai}) e
 * Azure OpenAI ({@code AI_PROVIDER=azure-openai}, usando deployment e api-version).
 */
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final RestClient http;
    private final String provider;
    private final String model;
    private final boolean azure;
    private final String path;

    public OpenAiCompatibleLlmClient(AppProperties.Ai props, boolean azure) {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new IllegalStateException("AI_API_KEY é obrigatório para AI_PROVIDER=" + props.provider());
        }
        this.azure = azure;
        this.provider = azure ? "azure-openai" : "openai";
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Math.min(props.timeout().toMillis(), 30_000));
        factory.setReadTimeout((int) props.timeout().toMillis());
        RestClient.Builder builder = RestClient.builder().requestFactory(factory);
        if (azure) {
            if (props.baseUrl() == null || props.baseUrl().isBlank() || props.azureDeployment() == null || props.azureDeployment().isBlank()) {
                throw new IllegalStateException("AI_BASE_URL e AI_AZURE_DEPLOYMENT são obrigatórios para azure-openai.");
            }
            builder.baseUrl(props.baseUrl()).defaultHeader("api-key", props.apiKey());
            this.path = "/openai/deployments/" + props.azureDeployment() + "/chat/completions?api-version=" + props.azureApiVersion();
            this.model = props.azureDeployment();
        } else {
            String base = props.baseUrl() == null || props.baseUrl().isBlank() ? "https://api.openai.com" : props.baseUrl();
            builder.baseUrl(base).defaultHeader("Authorization", "Bearer " + props.apiKey());
            this.path = "/v1/chat/completions";
            if (props.model() == null || props.model().isBlank()) {
                throw new IllegalStateException("AI_MODEL é obrigatório para AI_PROVIDER=openai.");
            }
            this.model = props.model();
        }
        this.http = builder.build();
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", request.system()));
        request.messages().forEach(m -> messages.add(Map.of("role", m.role(), "content", m.content())));
        Map<String, Object> body = azure
                ? Map.of("messages", messages, "max_tokens", request.maxTokens())
                : Map.of("model", model, "messages", messages, "max_tokens", request.maxTokens());
        try {
            JsonNode response = http.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(JsonNode.class);
            String text = response == null ? "" : response.path("choices").path(0).path("message").path("content").asText("");
            if (text.isBlank()) {
                throw new LlmException("Resposta vazia do provedor " + provider);
            }
            return new LlmResponse(text, provider, model, false);
        } catch (RestClientException e) {
            throw new LlmException("Falha no provedor " + provider, e);
        }
    }

    @Override
    public String provider() {
        return provider;
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
