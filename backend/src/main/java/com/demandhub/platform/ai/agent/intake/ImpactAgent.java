package com.demandhub.platform.ai.agent.intake;

import com.demandhub.platform.ai.agent.Agent;
import com.demandhub.platform.ai.agent.AgentSupport;
import com.demandhub.platform.ai.agent.PromptGuard;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Impact Agent: analisa o impacto declarado frente às evidências informadas. */
@Component
public class ImpactAgent implements Agent<ImpactAgent.Input, ImpactAgent.Output> {

    public record Input(String declaredImpact, String impactedAreas, String impactedUsersCount, String systemsInvolved,
                        boolean regulatory, String expectedBenefits) {}

    public record Output(String impactLevel, String analysis) {}

    private static final Set<String> LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    @Override
    public String name() {
        return "ImpactAgent";
    }

    @Override
    public String responsibility() {
        return "Analisar o impacto (áreas, usuários, sistemas, conformidade) e apontar se o nível declarado é coerente.";
    }

    @Override
    public String instructions() {
        return """
                Avalie o impacto. Compare o nível declarado com as evidências e explique divergências.
                Formato: {"impactLevel": "LOW|MEDIUM|HIGH|CRITICAL", "analysis": "análise objetiva em até 6 linhas"}
                """;
    }

    @Override
    public String userPrompt(Input in) {
        return "Impacto declarado: " + in.declaredImpact() + "\nExigência regulatória: " + in.regulatory() + "\n"
                + PromptGuard.untrusted("formulario", "Áreas impactadas: " + in.impactedAreas() + "\nUsuários impactados: "
                + in.impactedUsersCount() + "\nSistemas: " + in.systemsInvolved() + "\nBenefícios: " + in.expectedBenefits(), 6000);
    }

    @Override
    public Output parse(JsonNode json) {
        String level = AgentSupport.requiredText(json, "impactLevel", 10).toUpperCase();
        if (!LEVELS.contains(level)) {
            throw new InvalidAgentOutputException("Nível de impacto inválido: " + level);
        }
        return new Output(level, AgentSupport.requiredText(json, "analysis", 2000));
    }

    @Override
    public Output mockOutput(Input in) {
        String level = in.declaredImpact() == null ? "MEDIUM" : in.declaredImpact();
        StringBuilder sb = new StringBuilder("Impacto declarado: ").append(level).append('.');
        if (in.impactedAreas() != null) {
            sb.append(" Áreas afetadas: ").append(in.impactedAreas()).append('.');
        }
        if (in.impactedUsersCount() != null) {
            sb.append(" Usuários estimados: ").append(in.impactedUsersCount()).append('.');
        }
        if (in.regulatory()) {
            sb.append(" Há exigência regulatória, o que eleva o impacto.");
        }
        sb.append(" (Análise heurística — modo MOCK.)");
        return new Output(in.regulatory() && "LOW".equals(level) ? "MEDIUM" : level, sb.toString());
    }
}
