package com.demandhub.platform.ai.agent;

import com.demandhub.platform.ai.domain.AiAgentRun;
import com.demandhub.platform.ai.llm.LlmClient;
import com.demandhub.platform.ai.llm.LlmClient.LlmMessage;
import com.demandhub.platform.ai.llm.LlmClient.LlmRequest;
import com.demandhub.platform.ai.repository.AiRepositories.AiAgentRunRepository;
import com.demandhub.platform.shared.security.SecurityUtils;
import com.demandhub.platform.shared.util.JsonSupport;
import com.demandhub.platform.shared.util.Texts;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orquestrador central de agentes: monta prompt (preâmbulo de segurança + instruções + contexto mínimo),
 * executa no provedor configurado, valida a saída e registra a execução (AiAgentRun).
 * Falhas nunca propagam exceção para o processo de negócio — retornam {@code success=false}.
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final LlmClient llm;
    private final AiAgentRunRepository runs;
    private final JsonSupport json;
    private final Clock clock;

    public AgentOrchestrator(LlmClient llm, AiAgentRunRepository runs, JsonSupport json, Clock clock) {
        this.llm = llm;
        this.runs = runs;
        this.json = json;
        this.clock = clock;
    }

    public boolean isMockMode() {
        return llm.isMock();
    }

    public String providerDescription() {
        return llm.provider() + " / " + llm.model();
    }

    public <I, O> AgentResult<O> run(Agent<I, O> agent, I input, UUID demandId) {
        return run(agent, input, demandId, List.of());
    }

    /** @param history mensagens anteriores (chat), já limitadas pelo chamador */
    public <I, O> AgentResult<O> run(Agent<I, O> agent, I input, UUID demandId, List<LlmMessage> history) {
        long start = System.nanoTime();
        AiAgentRun run = new AiAgentRun();
        run.setDemandId(demandId);
        run.setAgent(agent.name());
        run.setProvider(llm.provider());
        run.setModel(llm.model());
        run.setMode(llm.isMock() ? "MOCK" : "REAL");
        run.setInput(Texts.truncate(json.write(input), 60_000));
        run.setCreatedBy(SecurityUtils.currentUserOptional().map(u -> u.id()).orElse(null));
        run.setCreatedAt(clock.instant());
        try {
            O output;
            if (llm.isMock()) {
                output = agent.mockOutput(input);
                run.setOutput(Texts.truncate(json.write(output), 60_000));
            } else {
                String system = PromptGuard.SECURITY_PREAMBLE + "\n" + agent.instructions();
                List<LlmMessage> messages = new java.util.ArrayList<>(history);
                messages.add(LlmMessage.user(agent.userPrompt(input)));
                String raw = llm.complete(new LlmRequest(system, messages, agent.maxTokens())).content();
                run.setOutput(Texts.truncate(raw, 60_000));
                output = agent.parse(extractJson(raw));
            }
            run.setStatus(AiAgentRun.Status.SUCCESS);
            return new AgentResult<>(output, save(run, start).getId(), run.getMode(), true, null);
        } catch (Agent.InvalidAgentOutputException e) {
            run.setStatus(AiAgentRun.Status.INVALID_OUTPUT);
            run.setError(Texts.truncate(e.getMessage(), 2000));
            log.warn("Agente {} retornou saída inválida: {}", agent.name(), e.getMessage());
            return new AgentResult<>(null, save(run, start).getId(), run.getMode(), false, "Saída inválida do agente.");
        } catch (RuntimeException e) {
            run.setStatus(AiAgentRun.Status.FAILED);
            run.setError(Texts.truncate(e.getClass().getSimpleName() + ": " + e.getMessage(), 2000));
            log.warn("Falha no agente {}: {}", agent.name(), e.getMessage());
            return new AgentResult<>(null, save(run, start).getId(), run.getMode(), false, "Não foi possível executar o agente de IA.");
        }
    }

    private AiAgentRun save(AiAgentRun run, long start) {
        run.setDurationMs((System.nanoTime() - start) / 1_000_000);
        return runs.save(run);
    }

    /** Extrai o primeiro objeto JSON da resposta (tolera cercas de código). */
    JsonNode extractJson(String raw) {
        String s = raw.trim();
        int first = s.indexOf('{');
        int last = s.lastIndexOf('}');
        if (first < 0 || last <= first) {
            throw new Agent.InvalidAgentOutputException("Resposta não contém objeto JSON.");
        }
        try {
            return json.mapper().readTree(s.substring(first, last + 1));
        } catch (Exception e) {
            throw new Agent.InvalidAgentOutputException("JSON malformado: " + e.getMessage());
        }
    }
}
