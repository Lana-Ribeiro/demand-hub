package com.demandhub.platform.ai.llm;

import java.util.List;

/**
 * Porta para provedores de LLM. A aplicação nunca depende de um provedor específico.
 * Implementações: Claude (SDK oficial Anthropic), OpenAI/Azure OpenAI (REST compatível), Mock (sem rede).
 */
public interface LlmClient {

    LlmResponse complete(LlmRequest request);

    String provider();

    String model();

    /** true = não chama provedor real; agentes usam heurística local explícita e marcam a saída como MOCK. */
    boolean isMock();

    record LlmMessage(String role, String content) {
        public static LlmMessage user(String content) {
            return new LlmMessage("user", content);
        }

        public static LlmMessage assistant(String content) {
            return new LlmMessage("assistant", content);
        }
    }

    record LlmRequest(String system, List<LlmMessage> messages, int maxTokens) {}

    record LlmResponse(String content, String provider, String model, boolean mock) {}

    /** Falha ao obter resposta utilizável do provedor (erro, recusa, timeout). */
    class LlmException extends RuntimeException {
        public LlmException(String message) {
            super(message);
        }

        public LlmException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
