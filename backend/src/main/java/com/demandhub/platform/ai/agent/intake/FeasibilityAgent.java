package com.demandhub.platform.ai.agent.intake;

import com.demandhub.platform.ai.agent.Agent;
import com.demandhub.platform.ai.agent.AgentSupport;
import com.demandhub.platform.ai.agent.PromptGuard;
import com.demandhub.platform.shared.util.Texts;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Feasibility Agent: apoia a análise de viabilidade e estima complexidade. */
@Component
public class FeasibilityAgent implements Agent<FeasibilityAgent.Input, FeasibilityAgent.Output> {

    public record Input(String demandType, boolean technical, String scope, String systemsInvolved,
                        String dependencies, String knownRisks, String desiredDate) {}

    public record Output(String complexity, String feasibility, List<String> risks, List<String> dependencies) {}

    private static final Set<String> LEVELS = Set.of("LOW", "MEDIUM", "HIGH");

    @Override
    public String name() {
        return "FeasibilityAgent";
    }

    @Override
    public String responsibility() {
        return "Apoiar a análise de viabilidade: complexidade estimada, riscos e dependências identificadas.";
    }

    @Override
    public String instructions() {
        return """
                Avalie a viabilidade e a complexidade estimada. Liste riscos e dependências identificáveis a partir do texto.
                Formato: {"complexity": "LOW|MEDIUM|HIGH", "feasibility": "parecer objetivo", "risks": ["..."], "dependencies": ["..."]}
                """;
    }

    @Override
    public String userPrompt(Input in) {
        return "Tipo: " + in.demandType() + " (técnica: " + in.technical() + ")\nPrazo desejado: " + in.desiredDate() + "\n"
                + PromptGuard.untrusted("formulario", "Escopo: " + in.scope() + "\nSistemas: " + in.systemsInvolved()
                + "\nDependências: " + in.dependencies() + "\nRiscos conhecidos: " + in.knownRisks(), 8000);
    }

    @Override
    public Output parse(JsonNode json) {
        String c = AgentSupport.requiredText(json, "complexity", 10).toUpperCase();
        if (!LEVELS.contains(c)) {
            throw new InvalidAgentOutputException("Complexidade inválida: " + c);
        }
        return new Output(c, AgentSupport.requiredText(json, "feasibility", 2000),
                AgentSupport.textList(json, "risks", 10, 500), AgentSupport.textList(json, "dependencies", 10, 500));
    }

    @Override
    public Output mockOutput(Input in) {
        int systems = Texts.isBlank(in.systemsInvolved()) ? 0 : in.systemsInvolved().split("[,;/]").length;
        String complexity = !in.technical() ? "LOW" : systems >= 3 ? "HIGH" : "MEDIUM";
        List<String> risks = new ArrayList<>(splitItems(in.knownRisks()));
        if (systems >= 2) {
            risks.add("Integração entre múltiplos sistemas (" + systems + ").");
        }
        return new Output(complexity, "Estimativa heurística baseada no número de sistemas e no tipo da demanda (modo MOCK).",
                risks, splitItems(in.dependencies()));
    }

    static List<String> splitItems(String text) {
        List<String> out = new ArrayList<>();
        if (Texts.isBlank(text)) {
            return out;
        }
        for (String s : text.split("\\R|;")) {
            String t = s.replaceFirst("^[-•*]\\s*", "").trim();
            if (!t.isEmpty() && out.size() < 10) {
                out.add(Texts.truncate(t, 300));
            }
        }
        return out;
    }
}
