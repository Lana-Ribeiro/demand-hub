package com.demandhub.platform.ai.agent.intake;

import com.demandhub.platform.ai.agent.Agent;
import com.demandhub.platform.ai.agent.AgentSupport;
import com.demandhub.platform.ai.agent.AgentSupport.FieldSuggestion;
import com.demandhub.platform.ai.agent.PromptGuard;
import com.demandhub.platform.shared.util.Texts;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Intake Agent: conversa com o solicitante para ajudar no preenchimento. Só sugere; nunca preenche sem aceite. */
@Component
public class IntakeAssistantAgent implements Agent<IntakeAssistantAgent.Input, IntakeAssistantAgent.Output> {

    public record Input(Map<String, String> form, List<String> missingRequired, String lastAssistantMessage, String userMessage) {}

    public record Output(String reply, List<FieldSuggestion> suggestions) {}

    /** Ordem natural de perguntas para os campos obrigatórios (usada também pela heurística MOCK). */
    static final Map<String, String> QUESTIONS = new LinkedHashMap<>();

    static {
        QUESTIONS.put("title", "Como você chamaria essa iniciativa em poucas palavras?");
        QUESTIONS.put("currentProblem", "Qual problema o processo atual apresenta?");
        QUESTIONS.put("impactedAreas", "Quem utiliza atualmente esse processo? Quais áreas são impactadas?");
        QUESTIONS.put("objective", "Qual é o objetivo principal da iniciativa?");
        QUESTIONS.put("expectedBenefits", "Qual resultado você espera obter?");
        QUESTIONS.put("justification", "Por que essa iniciativa é necessária agora?");
        QUESTIONS.put("impactLevel", "Como você avalia o impacto: baixo, médio, alto ou crítico?");
        QUESTIONS.put("urgency", "Qual a urgência: baixa, média ou alta?");
        QUESTIONS.put("demandType", "Essa demanda envolve desenvolvimento de sistema, melhoria, automação, integração, alocação de pessoas, mudança operacional, consultoria ou suporte?");
        QUESTIONS.put("sponsorName", "Quem é o patrocinador (executivo responsável) da iniciativa?");
        QUESTIONS.put("businessFocalPoint", "Quem será o ponto focal de negócio para dúvidas e validações?");
        QUESTIONS.put("requesterArea", "De qual diretoria ou área vem a solicitação?");
    }

    @Override
    public String name() {
        return "IntakeAgent";
    }

    @Override
    public String responsibility() {
        return "Auxiliar o solicitante no preenchimento: explicar campos, fazer perguntas objetivas, sugerir valores e apontar lacunas.";
    }

    @Override
    public String instructions() {
        return """
                Seu papel: assistente de abertura de demandas. Ajude o solicitante a preencher o formulário.
                - Faça UMA pergunta objetiva por vez, priorizando campos obrigatórios vazios. Não pergunte o que já está preenchido.
                - Explique termos e campos quando perguntado.
                - Quando a mensagem do usuário contiver informação para um campo, proponha uma sugestão. Sugestões são propostas:
                  o usuário decide se aceita. Nunca afirme que preencheu o formulário.
                - Aponte inconsistências entre o que foi dito e o que já está no formulário.
                - Use somente as chaves de campo do catálogo. Para impactLevel use LOW|MEDIUM|HIGH|CRITICAL; urgency LOW|MEDIUM|HIGH;
                  demandType um dos códigos: DEVELOPMENT, IMPROVEMENT, AUTOMATION, INTEGRATION, RESOURCE_ALLOCATION,
                  OPERATIONAL_CHANGE, CONSULTING, SUPPORT, OTHER; datas AAAA-MM-DD.
                Formato da resposta:
                {"reply": "texto para o usuário", "suggestions": [{"field": "chave", "value": "valor", "confidence": 0.0-1.0, "rationale": "por quê"}]}
                """;
    }

    @Override
    public String userPrompt(Input in) {
        return "Catálogo de campos:\n" + AgentSupport.fieldCatalog(false)
                + "\n\nEstado atual do formulário:\n" + AgentSupport.formSnapshot(in.form())
                + "\n\nCampos obrigatórios ainda vazios: " + String.join(", ", in.missingRequired())
                + "\n\nMensagem do usuário:\n" + PromptGuard.untrusted("chat", in.userMessage(), 4000);
    }

    @Override
    public Output parse(JsonNode json) {
        return new Output(AgentSupport.requiredText(json, "reply", 4000), AgentSupport.fieldSuggestions(json, "suggestions", false));
    }

    @Override
    public Output mockOutput(Input in) {
        List<FieldSuggestion> suggestions = new ArrayList<>();
        String msg = in.userMessage().trim();
        String answeredField = QUESTIONS.entrySet().stream()
                .filter(e -> in.lastAssistantMessage() != null && in.lastAssistantMessage().contains(e.getValue()))
                .map(Map.Entry::getKey).findFirst().orElse(null);
        if (answeredField != null) {
            suggestions.add(new FieldSuggestion(answeredField, mapValue(answeredField, msg), 0.6,
                    "Resposta do usuário à pergunta sobre este campo.", null));
        } else if (Texts.isBlank(in.form().get("objective")) && msg.length() > 15) {
            suggestions.add(new FieldSuggestion("objective", msg, 0.5, "Descrição inicial informada no chat.", null));
            if (Texts.isBlank(in.form().get("title"))) {
                String[] words = msg.replaceAll("(?i)^(quero|gostaria de|preciso)\\s+(criar\\s+)?(uma\\s+)?(iniciativa\\s+)?(para\\s+)?", "").split("\\s+");
                String title = String.join(" ", java.util.Arrays.copyOf(words, Math.min(words.length, 8)));
                suggestions.add(new FieldSuggestion("title", capitalize(title.replaceAll("[.,;]$", "")), 0.4, "Título derivado da descrição.", null));
            }
        }
        List<String> stillMissing = new ArrayList<>(in.missingRequired());
        suggestions.forEach(s -> stillMissing.remove(s.field()));
        String next = QUESTIONS.entrySet().stream().filter(e -> stillMissing.contains(e.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse(null);
        String prefix = suggestions.isEmpty() ? "" : "Entendi. Registrei uma sugestão para você revisar no formulário. ";
        String reply = next != null ? prefix + next
                : prefix + "Os campos obrigatórios parecem preenchidos. Revise as informações e, se estiver tudo certo, envie a demanda.";
        return new Output(reply, suggestions);
    }

    private static String mapValue(String field, String msg) {
        String n = Texts.normalize(msg);
        return switch (field) {
            case "impactLevel" -> n.contains("crit") ? "CRITICAL" : n.contains("alt") ? "HIGH" : n.contains("baix") ? "LOW" : "MEDIUM";
            case "urgency" -> n.contains("alt") ? "HIGH" : n.contains("baix") ? "LOW" : "MEDIUM";
            case "demandType" -> ClassificationAgent.guessType(msg);
            case "title" -> Texts.truncate(msg, 200);
            default -> msg;
        };
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
