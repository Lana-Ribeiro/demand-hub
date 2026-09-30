package com.demandhub.platform.ai.agent;

import java.util.UUID;

/** Resultado de uma execução de agente. {@code output} é nulo quando {@code success=false}. */
public record AgentResult<O>(O output, UUID runId, String mode, boolean success, String error) {

    public boolean isMock() {
        return "MOCK".equals(mode);
    }
}
