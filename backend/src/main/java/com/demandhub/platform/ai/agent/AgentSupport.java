package com.demandhub.platform.ai.agent;

import com.demandhub.platform.demand.domain.DemandField;
import com.demandhub.platform.shared.util.Texts;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Utilitários comuns de parsing/validação das saídas dos agentes. */
public final class AgentSupport {

    private AgentSupport() {}

    /** Sugestão de valor para um campo do formulário. */
    public record FieldSuggestion(String field, String value, double confidence, String rationale, String excerpt) {}

    /** Catálogo de campos em texto para o prompt (whitelist do que pode ser sugerido). */
    public static String fieldCatalog(boolean includeClassification) {
        return Arrays.stream(DemandField.values())
                .filter(f -> includeClassification || f.clientEditable())
                .map(f -> "- " + f.key() + " (" + f.type().name().toLowerCase() + (f.requiredAtSubmit() ? ", obrigatório" : "")
                        + "): " + f.label() + ". " + f.help())
                .collect(Collectors.joining("\n"));
    }

    public static String formSnapshot(Map<String, String> values) {
        return values.entrySet().stream()
                .map(e -> e.getKey() + ": " + (Texts.isBlank(e.getValue()) ? "(vazio)" : Texts.truncate(e.getValue(), 1500)))
                .collect(Collectors.joining("\n"));
    }

    public static String requiredText(JsonNode node, String field, int max) {
        JsonNode v = node.get(field);
        if (v == null || !v.isTextual() || v.asText().isBlank()) {
            throw new Agent.InvalidAgentOutputException("Campo obrigatório ausente na saída: " + field);
        }
        return Texts.truncate(v.asText().trim(), max);
    }

    public static String optionalText(JsonNode node, String field, int max) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.isTextual() ? v.asText() : v.toString();
        return s.isBlank() ? null : Texts.truncate(s.trim(), max);
    }

    public static List<String> textList(JsonNode node, String field, int maxItems, int maxLen) {
        List<String> out = new ArrayList<>();
        JsonNode arr = node.get(field);
        if (arr != null && arr.isArray()) {
            for (JsonNode item : arr) {
                if (out.size() >= maxItems) break;
                String s = item.isTextual() ? item.asText() : item.toString();
                if (!s.isBlank()) out.add(Texts.truncate(s.trim(), maxLen));
            }
        }
        return out;
    }

    public static double confidence(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || !v.isNumber()) {
            return 0.5;
        }
        return Math.max(0, Math.min(1, v.asDouble()));
    }

    /** Valida sugestões de campo contra a whitelist do catálogo; itens inválidos são descartados. */
    public static List<FieldSuggestion> fieldSuggestions(JsonNode node, String arrayField, boolean allowClassification) {
        List<FieldSuggestion> out = new ArrayList<>();
        JsonNode arr = node.get(arrayField);
        if (arr == null || !arr.isArray()) {
            return out;
        }
        for (JsonNode item : arr) {
            String key = optionalText(item, "field", 60);
            String value = optionalText(item, "value", 8000);
            if (key == null || value == null) continue;
            DemandField.byKey(key)
                    .filter(f -> allowClassification || f.clientEditable())
                    .filter(f -> value.length() <= f.maxLength())
                    .ifPresent(f -> out.add(new FieldSuggestion(f.key(), value, confidence(item, "confidence"),
                            optionalText(item, "rationale", 1000), optionalText(item, "excerpt", 1000))));
            if (out.size() >= 40) break;
        }
        return out;
    }
}
