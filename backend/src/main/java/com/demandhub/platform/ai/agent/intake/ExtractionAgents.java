package com.demandhub.platform.ai.agent.intake;

import com.demandhub.platform.ai.agent.Agent;
import com.demandhub.platform.ai.agent.AgentSupport;
import com.demandhub.platform.ai.agent.AgentSupport.FieldSuggestion;
import com.demandhub.platform.ai.agent.PromptGuard;
import com.demandhub.platform.config.AppProperties;
import com.demandhub.platform.shared.util.Texts;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Document Extraction Agent e Presentation Extraction Agent. */
public final class ExtractionAgents {

    private ExtractionAgents() {}

    public record Input(String fileName, String text) {}

    public record Output(List<FieldSuggestion> suggestions, String documentSummary) {}

    abstract static class Base implements Agent<Input, Output> {

        private final int maxChars;

        Base(AppProperties props) {
            this.maxChars = props.ai().maxDocumentChars();
        }

        abstract String sourceLabel();

        @Override
        public String userPrompt(Input in) {
            return "Catálogo de campos que podem ser sugeridos:\n" + AgentSupport.fieldCatalog(false)
                    + "\n\nArquivo: " + Texts.truncate(in.fileName(), 200) + "\n"
                    + PromptGuard.untrusted(sourceLabel(), in.text(), maxChars);
        }

        @Override
        public String instructions() {
            return specificInstructions() + """
                    Extraia somente informações explicitamente presentes. Para cada campo encontrado, informe o valor, a confiança
                    (0.0 a 1.0) e um trecho literal curto que comprove (excerpt). Não preencha campos sem evidência.
                    Valores: impactLevel LOW|MEDIUM|HIGH|CRITICAL; urgency LOW|MEDIUM|HIGH; datas AAAA-MM-DD; números sem símbolos.
                    Formato: {"suggestions": [{"field": "chave", "value": "valor", "confidence": 0.0, "excerpt": "trecho", "rationale": "..."}],
                              "documentSummary": "resumo de até 5 linhas do conteúdo"}
                    """;
        }

        abstract String specificInstructions();

        @Override
        public Output parse(JsonNode json) {
            return new Output(AgentSupport.fieldSuggestions(json, "suggestions", false),
                    AgentSupport.optionalText(json, "documentSummary", 2000));
        }

        @Override
        public Output mockOutput(Input in) {
            return new Output(HeuristicExtractor.extract(in.text()), Texts.truncate(in.text(), 400));
        }

        @Override
        public int maxTokens() {
            return 6000;
        }
    }

    @Component
    public static class DocumentExtractionAgent extends Base {
        public DocumentExtractionAgent(AppProperties props) {
            super(props);
        }

        @Override
        public String name() {
            return "DocumentExtractionAgent";
        }

        @Override
        public String responsibility() {
            return "Ler a documentação enviada (template) e mapear informações para os campos da demanda, com origem e confiança.";
        }

        @Override
        String sourceLabel() {
            return "documento_do_cliente";
        }

        @Override
        String specificInstructions() {
            return "Você analisa a documentação (template) enviada pelo cliente para abertura de iniciativa.\n";
        }
    }

    @Component
    public static class PresentationExtractionAgent extends Base {
        public PresentationExtractionAgent(AppProperties props) {
            super(props);
        }

        @Override
        public String name() {
            return "PresentationExtractionAgent";
        }

        @Override
        public String responsibility() {
            return "Ler a apresentação padrão (PPT/Canva) e mapear informações para os campos, permitindo comparar com o formulário.";
        }

        @Override
        String sourceLabel() {
            return "apresentacao_do_cliente";
        }

        @Override
        String specificInstructions() {
            return "Você analisa a apresentação padrão (PPT/Canva) do cliente. O texto vem de slides; títulos de slide indicam o assunto.\n";
        }
    }

    /** Heurística do modo MOCK: pares "Rótulo: valor" com sinônimos conhecidos. */
    static final class HeuristicExtractor {

        private static final Map<String, String> LABELS = new LinkedHashMap<>();

        static {
            LABELS.put("fora do escopo", "outOfScope");
            LABELS.put("centro de custo", "costCenter");
            LABELS.put("ponto focal tecnico", "technicalFocalPoint");
            LABELS.put("ponto focal", "businessFocalPoint");
            LABELS.put("nome da iniciativa", "title");
            LABELS.put("titulo", "title");
            LABELS.put("objetivo", "objective");
            LABELS.put("situacao atual", "currentProblem");
            LABELS.put("cenario atual", "currentProblem");
            LABELS.put("problema", "currentProblem");
            LABELS.put("justificativa", "justification");
            LABELS.put("beneficios", "expectedBenefits");
            LABELS.put("resultado esperado", "expectedBenefits");
            LABELS.put("escopo", "scopeDescription");
            LABELS.put("areas impactadas", "impactedAreas");
            LABELS.put("usuarios impactados", "impactedUsersCount");
            LABELS.put("sistemas", "systemsInvolved");
            LABELS.put("data desejada", "desiredDate");
            LABELS.put("prazo", "desiredDate");
            LABELS.put("patrocinador", "sponsorName");
            LABELS.put("sponsor", "sponsorName");
            LABELS.put("orcamento", "estimatedBudget");
            LABELS.put("investimento", "estimatedBudget");
            LABELS.put("diretoria", "requesterArea");
            LABELS.put("area solicitante", "requesterArea");
            LABELS.put("dependencias", "dependencies");
            LABELS.put("riscos", "knownRisks");
        }

        private static final Pattern LINE = Pattern.compile("^\\s*[-•*]?\\s*([^:\\n]{3,40})\\s*[:\\-–]\\s+(.{2,})$");
        private static final String[] MONTHS = {"janeiro", "fevereiro", "marco", "abril", "maio", "junho", "julho",
                "agosto", "setembro", "outubro", "novembro", "dezembro"};

        static List<FieldSuggestion> extract(String text) {
            List<FieldSuggestion> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            if (text == null) return out;
            for (String line : text.split("\\R")) {
                Matcher m = LINE.matcher(line);
                if (!m.matches()) continue;
                String label = Texts.normalize(m.group(1));
                String value = m.group(2).trim();
                for (Map.Entry<String, String> e : LABELS.entrySet()) {
                    if (label.startsWith(e.getKey()) && seen.add(e.getValue())) {
                        String v = convert(e.getValue(), value);
                        if (v != null) {
                            out.add(new FieldSuggestion(e.getValue(), v, 0.55, "Rótulo \"" + m.group(1).trim() + "\" encontrado no arquivo.",
                                    Texts.truncate(line.trim(), 300)));
                        }
                        break;
                    }
                }
            }
            return out;
        }

        static String convert(String field, String value) {
            return switch (field) {
                case "desiredDate" -> parseDate(value);
                case "estimatedBudget" -> {
                    String digits = value.replaceAll("[^0-9,.]", "");
                    yield digits.isEmpty() ? null : digits;
                }
                case "impactedUsersCount" -> {
                    String digits = value.replaceAll("[^0-9]", "");
                    yield digits.isEmpty() ? null : digits;
                }
                default -> Texts.truncate(value, 2000);
            };
        }

        static String parseDate(String value) {
            String n = Texts.normalize(value);
            Matcher iso = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})").matcher(value);
            if (iso.find()) return iso.group();
            Matcher br = Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{4})").matcher(value);
            if (br.find()) {
                return LocalDate.of(Integer.parseInt(br.group(3)), Integer.parseInt(br.group(2)), Integer.parseInt(br.group(1))).toString();
            }
            Matcher monthYear = Pattern.compile("(\\d{1,2})/(\\d{4})").matcher(value);
            if (monthYear.find()) {
                return LocalDate.of(Integer.parseInt(monthYear.group(2)), Integer.parseInt(monthYear.group(1)), 1).toString();
            }
            Matcher year = Pattern.compile("(\\d{4})").matcher(n);
            if (year.find()) {
                for (int i = 0; i < MONTHS.length; i++) {
                    if (n.contains(MONTHS[i])) {
                        return LocalDate.of(Integer.parseInt(year.group(1)), i + 1, 1).toString();
                    }
                }
            }
            return null;
        }
    }
}
