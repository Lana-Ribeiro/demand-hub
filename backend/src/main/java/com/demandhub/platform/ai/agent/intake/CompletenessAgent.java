package com.demandhub.platform.ai.agent.intake;

import com.demandhub.platform.ai.agent.Agent;
import com.demandhub.platform.ai.agent.AgentSupport;
import com.demandhub.platform.ai.agent.PromptGuard;
import com.demandhub.platform.demand.domain.DemandField;
import com.demandhub.platform.shared.util.Texts;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Completeness Agent: a obrigatoriedade é verificada deterministicamente pelo sistema; o agente avalia
 * a QUALIDADE (respostas vagas ou insuficientes) e sugere perguntas.
 */
@Component
public class CompletenessAgent implements Agent<CompletenessAgent.Input, CompletenessAgent.Output> {

    public record Input(Map<String, String> form, List<String> missingRequired) {}

    public record Gap(String field, String question) {}

    public record Output(List<Gap> gaps) {}

    @Override
    public String name() {
        return "CompletenessAgent";
    }

    @Override
    public String responsibility() {
        return "Identificar informações faltantes ou insuficientes e sugerir perguntas objetivas ao solicitante.";
    }

    @Override
    public String instructions() {
        return """
                Os campos obrigatórios vazios já foram identificados pelo sistema e estão listados. Avalie a QUALIDADE dos campos
                preenchidos: aponte respostas vagas, genéricas ou que não permitem avaliar a demanda, e formule uma pergunta objetiva
                para cada lacuna. No máximo 8 itens. Use as chaves de campo do formulário.
                Formato: {"gaps": [{"field": "chave", "question": "pergunta ao solicitante"}]}
                """;
    }

    @Override
    public String userPrompt(Input in) {
        return "Obrigatórios vazios (sistema): " + String.join(", ", in.missingRequired()) + "\n"
                + PromptGuard.untrusted("formulario", AgentSupport.formSnapshot(in.form()), 20000);
    }

    @Override
    public Output parse(JsonNode json) {
        List<Gap> gaps = new ArrayList<>();
        JsonNode arr = json.get("gaps");
        if (arr != null && arr.isArray()) {
            for (JsonNode g : arr) {
                String field = AgentSupport.optionalText(g, "field", 60);
                String q = AgentSupport.optionalText(g, "question", 1000);
                if (q != null && gaps.size() < 8) {
                    gaps.add(new Gap(field != null && DemandField.byKey(field).isPresent() ? field : null, q));
                }
            }
        }
        return new Output(gaps);
    }

    @Override
    public Output mockOutput(Input in) {
        List<Gap> gaps = new ArrayList<>();
        for (DemandField f : List.of(DemandField.OBJECTIVE, DemandField.CURRENT_PROBLEM, DemandField.EXPECTED_BENEFITS, DemandField.JUSTIFICATION)) {
            String v = in.form().get(f.key());
            if (!Texts.isBlank(v) && v.trim().length() < 40) {
                gaps.add(new Gap(f.key(), "O campo \"" + f.label() + "\" está breve. Pode detalhar com exemplos concretos e números?"));
            }
        }
        if (Texts.isBlank(in.form().get("desiredDate"))) {
            gaps.add(new Gap("desiredDate", "Existe algum prazo desejado ou data limite para a entrega?"));
        }
        return new Output(gaps);
    }
}
