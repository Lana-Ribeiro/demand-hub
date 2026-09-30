package com.demandhub.platform.ai.agent.intake;

import com.demandhub.platform.ai.agent.Agent;
import com.demandhub.platform.ai.agent.AgentSupport;
import com.demandhub.platform.ai.agent.PromptGuard;
import com.demandhub.platform.shared.util.Texts;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Classification Agent: sugere tipo de demanda e projeto dentre as opções configuradas. */
@Component
public class ClassificationAgent implements Agent<ClassificationAgent.Input, ClassificationAgent.Output> {

    public record Option(String code, String name, String description) {}

    public record Input(String title, String objective, String problem, String currentType, List<Option> types, List<Option> projects) {}

    public record Output(String typeCode, double typeConfidence, String typeRationale,
                         String projectCode, double projectConfidence, String projectRationale) {}

    @Override
    public String name() {
        return "ClassificationAgent";
    }

    @Override
    public String responsibility() {
        return "Sugerir o tipo de demanda e o projeto mais adequados, dentre os configurados, com justificativa.";
    }

    @Override
    public String instructions() {
        return """
                Classifique a demanda. Escolha typeCode SOMENTE entre os tipos listados e projectCode SOMENTE entre os projetos listados
                (ou null se nenhum projeto se aplicar com segurança). Nem toda demanda é desenvolvimento: alocação de pessoas,
                mudança operacional, consultoria (ex.: mapeamento de processos) e suporte são tipos próprios.
                Formato: {"typeCode": "...", "typeConfidence": 0.0, "typeRationale": "...", "projectCode": "..." ou null,
                          "projectConfidence": 0.0, "projectRationale": "..."}
                """;
    }

    @Override
    public String userPrompt(Input in) {
        return "Tipos disponíveis:\n" + options(in.types()) + "\n\nProjetos disponíveis:\n" + options(in.projects())
                + "\n\nTipo informado pelo solicitante: " + (in.currentType() == null ? "(não informado)" : in.currentType())
                + "\n\nDemanda:\n" + PromptGuard.untrusted("formulario",
                "Título: " + in.title() + "\nObjetivo: " + in.objective() + "\nProblema: " + in.problem(), 6000);
    }

    private static String options(List<Option> opts) {
        return opts.stream().map(o -> "- " + o.code() + ": " + o.name() + (o.description() == null ? "" : " — " + o.description()))
                .collect(Collectors.joining("\n"));
    }

    /** Valida a saída contra as opções da entrada (o serviço chama este método com a entrada original). */
    public Output validate(Output out, Input in) {
        Set<String> typeCodes = in.types().stream().map(Option::code).collect(Collectors.toSet());
        Set<String> projectCodes = in.projects().stream().map(Option::code).collect(Collectors.toSet());
        String type = typeCodes.contains(out.typeCode()) ? out.typeCode() : null;
        String project = out.projectCode() != null && projectCodes.contains(out.projectCode()) ? out.projectCode() : null;
        return new Output(type, out.typeConfidence(), out.typeRationale(), project, out.projectConfidence(), out.projectRationale());
    }

    @Override
    public Output parse(JsonNode json) {
        return new Output(AgentSupport.requiredText(json, "typeCode", 40), AgentSupport.confidence(json, "typeConfidence"),
                AgentSupport.optionalText(json, "typeRationale", 1000), AgentSupport.optionalText(json, "projectCode", 40),
                AgentSupport.confidence(json, "projectConfidence"), AgentSupport.optionalText(json, "projectRationale", 1000));
    }

    @Override
    public Output mockOutput(Input in) {
        String text = in.title() + " " + in.objective() + " " + in.problem();
        String type = guessType(text);
        Set<String> tokens = Texts.tokens(text);
        Option best = in.projects().stream()
                .max(Comparator.comparingDouble(p -> Texts.jaccard(tokens, Texts.tokens(p.code() + " " + p.name() + " " + p.description()))))
                .orElse(null);
        double score = best == null ? 0 : Texts.jaccard(tokens, Texts.tokens(best.code() + " " + best.name() + " " + best.description()));
        String project = score > 0.02 ? best.code() : null;
        return new Output(type, 0.55, "Heurística por palavras-chave (modo MOCK).",
                project, project == null ? 0 : Math.min(0.8, 0.3 + score),
                project == null ? "Nenhum projeto com termos em comum." : "Termos em comum com a descrição do projeto (modo MOCK).");
    }

    /** Heurística de palavras-chave (modo MOCK e apoio ao assistente). */
    public static String guessType(String text) {
        String n = Texts.normalize(text);
        if (n.contains("alocac") || n.contains("alocar") || n.contains("contratar pessoa") || n.contains("recurso humano")) return "RESOURCE_ALLOCATION";
        if (n.contains("mapeamento") || n.contains("consultoria") || n.contains("diagnostico")) return "CONSULTING";
        if (n.contains("suporte") || n.contains("apoio pontual")) return "SUPPORT";
        if (n.contains("mudanca operacional") || n.contains("procedimento") || n.contains("escala")) return "OPERATIONAL_CHANGE";
        if (n.contains("integr")) return "INTEGRATION";
        if (n.contains("automat") || n.contains("automac")) return "AUTOMATION";
        if (n.contains("melhori") || n.contains("evoluc") || n.contains("ajuste")) return "IMPROVEMENT";
        if (n.contains("sistema") || n.contains("desenvolv") || n.contains("aplicac") || n.contains("ferramenta")) return "DEVELOPMENT";
        return "OTHER";
    }
}
