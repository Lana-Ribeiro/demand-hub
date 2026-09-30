package com.demandhub.platform.ai.llm;

/**
 * Provedor MOCK para desenvolvimento local: não chama rede. Os agentes detectam {@link #isMock()} e usam
 * heurísticas locais explícitas; toda saída fica marcada como MOCK (nunca apresentada como IA real).
 */
public class MockLlmClient implements LlmClient {

    @Override
    public LlmResponse complete(LlmRequest request) {
        throw new LlmException("MockLlmClient não gera texto: agentes devem usar o fallback heurístico em modo MOCK.");
    }

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public String model() {
        return "heuristic-v1";
    }

    @Override
    public boolean isMock() {
        return true;
    }
}
