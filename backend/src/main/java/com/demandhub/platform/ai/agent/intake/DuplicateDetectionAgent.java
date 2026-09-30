package com.demandhub.platform.ai.agent.intake;

import com.demandhub.platform.ai.agent.Agent;
import com.demandhub.platform.ai.agent.AgentSupport;
import com.demandhub.platform.ai.agent.PromptGuard;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Duplicate Detection Agent: os candidatos são selecionados deterministicamente (similaridade lexical, incluindo legado);
 * o agente apenas julga e explica a semelhança.
 */
@Component
public class DuplicateDetectionAgent implements Agent<DuplicateDetectionAgent.Input, DuplicateDetectionAgent.Output> {

    public record Candidate(String ref, String title, String objective, double score) {}

    public record Input(String title, String objective, List<Candidate> candidates) {}

    public record Verdict(String ref, boolean likelyDuplicate, String rationale) {}

    public record Output(List<Verdict> verdicts) {}

    @Override
    public String name() {
        return "DuplicateAgent";
    }

    @Override
    public String responsibility() {
        return "Avaliar se demandas candidatas (selecionadas por similaridade, inclusive legado) são duplicadas ou semelhantes.";
    }

    @Override
    public String instructions() {
        return """
                Compare a nova demanda com cada candidata e diga se é provável duplicidade (mesmo objetivo/escopo) ou apenas semelhante.
                Use somente as referências (ref) fornecidas.
                Formato: {"verdicts": [{"ref": "...", "likelyDuplicate": true, "rationale": "..."}]}
                """;
    }

    @Override
    public String userPrompt(Input in) {
        String candidates = in.candidates().stream()
                .map(c -> "ref=" + c.ref() + " | título: " + c.title() + " | objetivo: " + c.objective())
                .collect(Collectors.joining("\n"));
        return PromptGuard.untrusted("nova_demanda", "Título: " + in.title() + "\nObjetivo: " + in.objective(), 4000) + "\n"
                + PromptGuard.untrusted("candidatas", candidates, 12000);
    }

    @Override
    public Output parse(JsonNode json) {
        List<Verdict> out = new ArrayList<>();
        JsonNode arr = json.get("verdicts");
        if (arr != null && arr.isArray()) {
            for (JsonNode v : arr) {
                String ref = AgentSupport.optionalText(v, "ref", 100);
                if (ref != null) {
                    out.add(new Verdict(ref, v.path("likelyDuplicate").asBoolean(false), AgentSupport.optionalText(v, "rationale", 1000)));
                }
            }
        }
        return new Output(out);
    }

    @Override
    public Output mockOutput(Input in) {
        return new Output(in.candidates().stream()
                .map(c -> new Verdict(c.ref(), c.score() >= 0.5,
                        "Similaridade lexical de " + Math.round(c.score() * 100) + "% entre título/objetivo (modo MOCK)."))
                .toList());
    }
}
