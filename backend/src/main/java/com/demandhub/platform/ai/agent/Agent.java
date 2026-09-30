package com.demandhub.platform.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Agente especializado: recebe contexto mínimo tipado, devolve saída estruturada validada.
 * Agentes NÃO persistem nada e NÃO alteram estado — somente os serviços da aplicação fazem isso.
 *
 * @param <I> entrada mínima (record serializável — é gravada em ai_agent_runs)
 * @param <O> saída validada
 */
public interface Agent<I, O> {

    /** Nome estável, ex.: "SummaryAgent". */
    String name();

    /** Responsabilidade única do agente (exibida na Administração). */
    String responsibility();

    /** Instruções específicas do agente (o prefixo de segurança comum é adicionado pelo orquestrador). */
    String instructions();

    /** Prompt do usuário construído somente com a entrada mínima. Conteúdo não confiável deve usar {@link PromptGuard}. */
    String userPrompt(I input);

    /** Converte e valida o JSON retornado. Lança {@link InvalidAgentOutputException} se inválido. */
    O parse(JsonNode json);

    /** Heurística local explícita usada somente com o provedor MOCK. */
    O mockOutput(I input);

    default int maxTokens() {
        return 4000;
    }

    class InvalidAgentOutputException extends RuntimeException {
        public InvalidAgentOutputException(String message) {
            super(message);
        }
    }
}
