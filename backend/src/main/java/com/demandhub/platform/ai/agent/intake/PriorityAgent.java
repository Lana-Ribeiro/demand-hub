package com.demandhub.platform.ai.agent.intake;

import com.demandhub.platform.ai.agent.Agent;
import com.demandhub.platform.ai.agent.AgentSupport;
import com.demandhub.platform.ai.agent.PromptGuard;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Priority Agent: sugere prioridade com base na política configurada (dado, não regra no prompt).
 * Recebe apenas o contexto mínimo necessário.
 */
@Component
public class PriorityAgent implements Agent<PriorityAgent.Input, PriorityAgent.Output> {

    public record Policy(String code, String name, String policy) {}

    public record Input(String summary, String impactLevel, String urgency, String desiredDate, boolean regulatory,
                        String estimatedBudget, String dependencies, List<Policy> policies, String today) {}

    public record Output(String priority, double confidence, String rationale) {}

    @Override
    public String name() {
        return "PriorityAgent";
    }

    @Override
    public String responsibility() {
        return "Sugerir prioridade (P1–P4) conforme a política de prioridades configurada. A decisão final é do PMO.";
    }

    @Override
    public String instructions() {
        return """
                Sugira a prioridade da demanda usando EXCLUSIVAMENTE a política de prioridades fornecida.
                Formato: {"priority": "P1|P2|P3|P4", "confidence": 0.0, "rationale": "justificativa citando a política"}
                """;
    }

    @Override
    public String userPrompt(Input in) {
        String policies = in.policies().stream().map(p -> "- " + p.code() + " (" + p.name() + "): " + p.policy())
                .collect(Collectors.joining("\n"));
        return "Política de prioridades:\n" + policies + "\n\nData de hoje: " + in.today()
                + "\nImpacto declarado: " + in.impactLevel() + "\nUrgência declarada: " + in.urgency()
                + "\nPrazo desejado: " + in.desiredDate() + "\nExigência regulatória: " + in.regulatory()
                + "\nOrçamento estimado: " + in.estimatedBudget() + "\n"
                + PromptGuard.untrusted("resumo", "Resumo: " + in.summary() + "\nDependências: " + in.dependencies(), 6000);
    }

    @Override
    public Output parse(JsonNode json) {
        String p = AgentSupport.requiredText(json, "priority", 5).toUpperCase();
        if (!p.matches("P[1-4]")) {
            throw new InvalidAgentOutputException("Prioridade inválida: " + p);
        }
        return new Output(p, AgentSupport.confidence(json, "confidence"), AgentSupport.optionalText(json, "rationale", 1500));
    }

    @Override
    public Output mockOutput(Input in) {
        int score = switch (in.impactLevel() == null ? "" : in.impactLevel()) {
            case "CRITICAL" -> 3;
            case "HIGH" -> 2;
            case "MEDIUM" -> 1;
            default -> 0;
        };
        score += switch (in.urgency() == null ? "" : in.urgency()) {
            case "HIGH" -> 2;
            case "MEDIUM" -> 1;
            default -> 0;
        };
        if (in.regulatory()) {
            score += 3;
        }
        if (in.desiredDate() != null && in.today() != null
                && ChronoUnit.DAYS.between(LocalDate.parse(in.today()), LocalDate.parse(in.desiredDate())) < 90) {
            score += 1;
        }
        String p = score >= 6 ? "P1" : score >= 4 ? "P2" : score >= 2 ? "P3" : "P4";
        return new Output(p, 0.5, "Pontuação heurística (impacto, urgência, regulatório, prazo) — modo MOCK.");
    }
}
