package com.demandhub.platform.ai.agent.intake;

import com.demandhub.platform.ai.agent.Agent;
import com.demandhub.platform.ai.agent.AgentSupport;
import com.demandhub.platform.ai.agent.PromptGuard;
import com.demandhub.platform.shared.util.Texts;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Summary Agent: gera o resumo executivo estruturado (visão resumida do PMO). */
@Component
public class SummaryAgent implements Agent<SummaryAgent.Input, SummaryAgent.Output> {

    public record Input(Map<String, String> form, String documentHighlights) {}

    public record Output(String executiveSummary, String objective, String problem, String expectedBenefit, String scope,
                         String impact, String deadline, String budget, List<String> risks, List<String> dependencies) {}

    @Override
    public String name() {
        return "SummaryAgent";
    }

    @Override
    public String responsibility() {
        return "Gerar o resumo executivo estruturado (objetivo, problema, benefício, impacto, prazo, riscos, dependências).";
    }

    @Override
    public String instructions() {
        return """
                Produza um resumo executivo fiel aos dados. Não acrescente informações que não estejam nos dados.
                Formato: {"executiveSummary": "3 a 5 linhas", "objective": "...", "problem": "...", "expectedBenefit": "...",
                          "scope": "...", "impact": "...", "deadline": "...", "budget": "...", "risks": ["..."], "dependencies": ["..."]}
                Use "Não informado" quando o dado não existir.
                """;
    }

    @Override
    public String userPrompt(Input in) {
        return PromptGuard.untrusted("formulario", AgentSupport.formSnapshot(in.form()), 20000)
                + (Texts.isBlank(in.documentHighlights()) ? "" : "\n" + PromptGuard.untrusted("documentos", in.documentHighlights(), 8000));
    }

    @Override
    public Output parse(JsonNode json) {
        return new Output(AgentSupport.requiredText(json, "executiveSummary", 3000),
                AgentSupport.optionalText(json, "objective", 2000), AgentSupport.optionalText(json, "problem", 2000),
                AgentSupport.optionalText(json, "expectedBenefit", 2000), AgentSupport.optionalText(json, "scope", 2000),
                AgentSupport.optionalText(json, "impact", 1000), AgentSupport.optionalText(json, "deadline", 500),
                AgentSupport.optionalText(json, "budget", 500),
                AgentSupport.textList(json, "risks", 10, 500), AgentSupport.textList(json, "dependencies", 10, 500));
    }

    @Override
    public Output mockOutput(Input in) {
        Map<String, String> f = in.form();
        String summary = "%s. Objetivo: %s Benefício esperado: %s".formatted(or(f.get("title")),
                Texts.firstSentence(or(f.get("objective")), 300), Texts.firstSentence(or(f.get("expectedBenefits")), 300));
        String budget = "true".equals(f.get("hasBudgetImpact")) ? "R$ " + or(f.get("estimatedBudget")) : "Sem impacto orçamentário informado";
        return new Output(summary + " (Resumo heurístico — modo MOCK.)",
                or(f.get("objective")), or(f.get("currentProblem")), or(f.get("expectedBenefits")), or(f.get("scopeDescription")),
                or(f.get("impactLevel")) + " — " + or(f.get("impactedAreas")),
                f.get("desiredDate") == null ? "Não informado" : f.get("desiredDate"), budget,
                FeasibilityAgent.splitItems(f.get("knownRisks")), FeasibilityAgent.splitItems(f.get("dependencies")));
    }

    private static String or(String s) {
        return Texts.isBlank(s) ? "Não informado" : s;
    }
}
