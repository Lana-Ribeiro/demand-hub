package com.demandhub.platform.ai.llm;

import com.demandhub.platform.config.AppProperties;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Seleção do provedor por AI_PROVIDER (anthropic | openai | azure-openai | mock). */
@Configuration
public class LlmClientConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmClientConfig.class);

    @Bean
    public LlmClient llmClient(AppProperties props) {
        AppProperties.Ai ai = props.ai();
        String provider = ai.provider() == null ? "mock" : ai.provider().toLowerCase(Locale.ROOT);
        LlmClient client = switch (provider) {
            case "anthropic", "claude" -> new AnthropicLlmClient(ai);
            case "openai" -> new OpenAiCompatibleLlmClient(ai, false);
            case "azure-openai", "azure" -> new OpenAiCompatibleLlmClient(ai, true);
            case "mock" -> new MockLlmClient();
            default -> throw new IllegalStateException("AI_PROVIDER desconhecido: " + provider);
        };
        log.info("Provedor de IA: {} (modelo {}){}", client.provider(), client.model(), client.isMock() ? " — MODO MOCK" : "");
        return client;
    }
}
