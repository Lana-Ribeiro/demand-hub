package com.demandhub.platform.ai.agent.technical;

import com.demandhub.platform.ai.agent.Agent;
import com.demandhub.platform.ai.agent.AgentSupport;
import com.demandhub.platform.ai.agent.PromptGuard;
import com.demandhub.platform.refinement.domain.RefinementEntities.DecisionType;
import com.demandhub.platform.refinement.domain.RefinementEntities.ItemType;
import com.demandhub.platform.shared.util.Texts;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Agentes de refinamento, arquitetura, especificação, prompt técnico e documentação. */
public final class TechnicalAgents {

    private TechnicalAgents() {}

    private static final Set<String> ITEM_TYPES = Arrays.stream(ItemType.values()).map(Enum::name).collect(Collectors.toSet());
    private static final Set<String> DECISION_TYPES = Arrays.stream(DecisionType.values()).map(Enum::name).collect(Collectors.toSet());

    // ------------------------------------------------------------------ Refinement Agent

    @Component
    public static class RefinementAgent implements Agent<RefinementAgent.Input, RefinementAgent.Output> {

        public record Input(String title, String objective, String meetingNotes, List<TechnicalContext.Item> existingItems) {}

        public record Proposal(String type, String description) {}

        public record Output(List<Proposal> items, List<Proposal> decisions) {}

        @Override
        public String name() {
            return "RefinementAgent";
        }

        @Override
        public String responsibility() {
            return "Organizar notas de reuniões e documentação em requisitos, critérios de aceite, dependências, riscos, dúvidas e decisões.";
        }

        @Override
        public String instructions() {
            return """
                    Organize as notas da reunião de refinamento. Classifique cada informação relevante como item:
                    FUNCTIONAL_REQUIREMENT, NON_FUNCTIONAL_REQUIREMENT, ACCEPTANCE_CRITERION, DEPENDENCY, RISK, QUESTION, PENDING_ITEM;
                    e decisões como BUSINESS, ARCHITECTURAL ou TECHNICAL. Não repita itens já existentes. Critérios de aceite devem ser verificáveis.
                    Formato: {"items": [{"type": "...", "description": "..."}], "decisions": [{"type": "...", "description": "..."}]}
                    """;
        }

        @Override
        public String userPrompt(Input in) {
            return "Demanda: " + in.title() + "\nObjetivo: " + in.objective() + "\nItens já registrados:\n"
                    + TechnicalContext.bullets(in.existingItems()) + "\n" + PromptGuard.untrusted("notas_reuniao", in.meetingNotes(), 20000);
        }

        @Override
        public Output parse(JsonNode json) {
            return new Output(proposals(json, "items", ITEM_TYPES), proposals(json, "decisions", DECISION_TYPES));
        }

        private static List<Proposal> proposals(JsonNode json, String field, Set<String> allowed) {
            List<Proposal> out = new ArrayList<>();
            JsonNode arr = json.get(field);
            if (arr != null && arr.isArray()) {
                for (JsonNode n : arr) {
                    String type = AgentSupport.optionalText(n, "type", 40);
                    String desc = AgentSupport.optionalText(n, "description", 2000);
                    if (type != null && desc != null && allowed.contains(type.toUpperCase()) && out.size() < 40) {
                        out.add(new Proposal(type.toUpperCase(), desc));
                    }
                }
            }
            return out;
        }

        @Override
        public Output mockOutput(Input in) {
            List<Proposal> items = new ArrayList<>();
            List<Proposal> decisions = new ArrayList<>();
            for (String raw : Texts.isBlank(in.meetingNotes()) ? new String[0] : in.meetingNotes().split("\\R")) {
                String line = raw.replaceFirst("^\\s*[-•*\\d.)]+\\s*", "").trim();
                if (line.length() < 8) continue;
                String n = Texts.normalize(line);
                if (n.startsWith("decid") || n.contains("ficou decidido") || n.contains("decisao")) {
                    decisions.add(new Proposal(n.contains("arquitet") || n.contains("api") ? "ARCHITECTURAL" : "BUSINESS", line));
                } else if (line.endsWith("?") || n.startsWith("duvida")) {
                    items.add(new Proposal("QUESTION", line));
                } else if (n.contains("criterio") || n.startsWith("aceite") || n.startsWith("dado que")) {
                    items.add(new Proposal("ACCEPTANCE_CRITERION", line));
                } else if (n.contains("risco")) {
                    items.add(new Proposal("RISK", line));
                } else if (n.contains("depende")) {
                    items.add(new Proposal("DEPENDENCY", line));
                } else if (n.contains("desempenho") || n.contains("seguranca") || n.contains("disponibilidade") || n.contains("tempo de resposta")) {
                    items.add(new Proposal("NON_FUNCTIONAL_REQUIREMENT", line));
                } else if (n.contains("pendente") || n.contains("pendencia")) {
                    items.add(new Proposal("PENDING_ITEM", line));
                } else if (n.contains("deve") || n.contains("permitir") || n.contains("precisa")) {
                    items.add(new Proposal("FUNCTIONAL_REQUIREMENT", line));
                }
            }
            return new Output(items, decisions);
        }
    }

    // ------------------------------------------------------------------ Architecture Agent

    @Component
    public static class ArchitectureAgent implements Agent<TechnicalContext, ArchitectureAgent.Output> {

        public record Output(String proposal, List<String> components, List<String> integrations, List<String> risks,
                             List<String> impacts, List<String> suggestedDecisions) {}

        @Override
        public String name() {
            return "ArchitectureAgent";
        }

        @Override
        public String responsibility() {
            return "Apoiar a arquitetura: componentes, integrações, riscos, impactos e proposta técnica. O arquiteto humano aprova.";
        }

        @Override
        public String instructions() {
            return """
                    Proponha uma arquitetura para atender aos requisitos. Seja concreto e baseado nos dados; aponte lacunas.
                    Formato: {"proposal": "texto (markdown permitido)", "components": ["..."], "integrations": ["..."],
                              "risks": ["..."], "impacts": ["..."], "suggestedDecisions": ["..."]}
                    """;
        }

        @Override
        public String userPrompt(TechnicalContext in) {
            return PromptGuard.untrusted("demanda", in.asText(), 30000);
        }

        @Override
        public Output parse(JsonNode json) {
            return new Output(AgentSupport.requiredText(json, "proposal", 12000), AgentSupport.textList(json, "components", 20, 500),
                    AgentSupport.textList(json, "integrations", 20, 500), AgentSupport.textList(json, "risks", 20, 500),
                    AgentSupport.textList(json, "impacts", 20, 500), AgentSupport.textList(json, "suggestedDecisions", 20, 500));
        }

        @Override
        public Output mockOutput(TechnicalContext in) {
            List<String> systems = Texts.isBlank(in.systems()) ? List.of()
                    : Arrays.stream(in.systems().split("[,;/]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            List<String> integrations = systems.stream().map(s -> "Integração com " + s + " (contrato de API a definir)").toList();
            List<String> risks = new ArrayList<>(in.ofType("RISK").stream().map(TechnicalContext.Item::description).toList());
            if (systems.size() > 1) risks.add("Acoplamento entre " + systems.size() + " sistemas envolvidos.");
            return new Output("Proposta preliminar (modo MOCK): solução modular para \"" + in.title() + "\", com camada de serviço "
                    + "dedicada, persistência própria e integrações via API REST com os sistemas envolvidos. Validar com o arquiteto.",
                    List.of("Interface de usuário", "Serviço de aplicação", "Banco de dados", "Camada de integração"),
                    integrations, risks, List.of("Impacto nos processos das áreas: " + TechnicalContext.n(in.objective())),
                    List.of("Definir padrão de autenticação entre sistemas", "Definir estratégia de versionamento das APIs"));
        }
    }

    // ------------------------------------------------------------------ Technical Specification Agent

    @Component
    public static class TechnicalSpecificationAgent implements Agent<TechnicalContext, MarkdownOutput> {

        @Override
        public String name() {
            return "TechnicalSpecificationAgent";
        }

        @Override
        public String responsibility() {
            return "Transformar refinamento e arquitetura em especificação técnica implementável.";
        }

        @Override
        public String instructions() {
            return """
                    Escreva uma especificação técnica em Markdown com: Contexto, Objetivo, Escopo/Fora do escopo, Requisitos funcionais,
                    Requisitos não funcionais, Regras de negócio, Arquitetura/Componentes, Integrações e APIs, Dados, Critérios de aceite,
                    Estratégia de testes, Riscos e Pendências. Não invente requisitos: marque lacunas como "A definir".
                    Formato: {"markdown": "..."}
                    """;
        }

        @Override
        public String userPrompt(TechnicalContext in) {
            return PromptGuard.untrusted("demanda", in.asText(), 30000);
        }

        @Override
        public MarkdownOutput parse(JsonNode json) {
            return new MarkdownOutput(AgentSupport.requiredText(json, "markdown", 60000));
        }

        @Override
        public MarkdownOutput mockOutput(TechnicalContext in) {
            return new MarkdownOutput("""
                    # Especificação técnica — %s

                    > Documento gerado em modo MOCK a partir dos dados registrados. Revisar antes de usar.

                    ## Contexto
                    %s

                    ## Objetivo
                    %s

                    ## Escopo
                    %s

                    **Fora do escopo:** %s

                    ## Requisitos funcionais
                    %s

                    ## Requisitos não funcionais
                    %s

                    ## Critérios de aceite
                    %s

                    ## Arquitetura
                    %s

                    ## Decisões
                    %s

                    ## Riscos e pendências
                    %s
                    """.formatted(in.title(), TechnicalContext.n(in.problem()), TechnicalContext.n(in.objective()),
                    TechnicalContext.n(in.scope()), TechnicalContext.n(in.outOfScope()),
                    TechnicalContext.bullets(in.ofType("FUNCTIONAL_REQUIREMENT")), TechnicalContext.bullets(in.ofType("NON_FUNCTIONAL_REQUIREMENT")),
                    TechnicalContext.bullets(in.ofType("ACCEPTANCE_CRITERION")), TechnicalContext.n(in.architecture()),
                    TechnicalContext.bullets(in.decisions()), TechnicalContext.bullets(in.ofType("RISK"))));
        }

        @Override
        public int maxTokens() {
            return 8000;
        }
    }

    // ------------------------------------------------------------------ Prompt Engineer Agent

    /** Gera apenas as orientações de implementação; a estrutura do prompt técnico é montada deterministicamente. */
    @Component
    public static class PromptEngineerAgent implements Agent<TechnicalContext, PromptEngineerAgent.Output> {

        public record Output(List<String> implementationSteps, List<String> affectedAreas, List<String> testScenarios) {}

        @Override
        public String name() {
            return "PromptEngineerAgent";
        }

        @Override
        public String responsibility() {
            return "Gerar orientações de implementação (passos, áreas afetadas, cenários de teste) para o prompt técnico estruturado.";
        }

        @Override
        public String instructions() {
            return """
                    Com base na demanda refinada, proponha passos de implementação ordenados, áreas/arquivos provavelmente afetados
                    (em termos de componentes, sem inventar caminhos específicos) e cenários de teste derivados dos critérios de aceite.
                    Formato: {"implementationSteps": ["..."], "affectedAreas": ["..."], "testScenarios": ["..."]}
                    """;
        }

        @Override
        public String userPrompt(TechnicalContext in) {
            return PromptGuard.untrusted("demanda", in.asText(), 30000);
        }

        @Override
        public Output parse(JsonNode json) {
            return new Output(AgentSupport.textList(json, "implementationSteps", 30, 500),
                    AgentSupport.textList(json, "affectedAreas", 30, 300), AgentSupport.textList(json, "testScenarios", 30, 500));
        }

        @Override
        public Output mockOutput(TechnicalContext in) {
            List<String> tests = in.ofType("ACCEPTANCE_CRITERION").stream().map(c -> "Validar: " + c.description()).toList();
            return new Output(List.of("Analisar o código existente e os padrões do repositório",
                    "Modelar dados e migrations necessárias", "Implementar serviços e regras de negócio com testes unitários",
                    "Implementar APIs/integrações", "Implementar interface de usuário", "Executar testes e revisar critérios de aceite"),
                    List.of("Camada de dados", "Serviços de aplicação", "APIs", "Interface de usuário"),
                    tests.isEmpty() ? List.of("Definir cenários a partir dos critérios de aceite (nenhum registrado)") : tests);
        }
    }

    // ------------------------------------------------------------------ Documentation Agents

    public record MarkdownOutput(String markdown) {}

    public record DocumentationInput(TechnicalContext context, String audience) {}

    abstract static class DocumentationAgent implements Agent<DocumentationInput, MarkdownOutput> {

        @Override
        public String userPrompt(DocumentationInput in) {
            return PromptGuard.untrusted("demanda", in.context().asText() + "\nResumo da execução:\n"
                    + TechnicalContext.n(in.context().executionSummary()), 30000);
        }

        @Override
        public MarkdownOutput parse(JsonNode json) {
            return new MarkdownOutput(AgentSupport.requiredText(json, "markdown", 60000));
        }

        @Override
        public int maxTokens() {
            return 8000;
        }
    }

    @Component
    public static class TechnicalDocumentationAgent extends DocumentationAgent {
        @Override
        public String name() {
            return "TechnicalDocumentationAgent";
        }

        @Override
        public String responsibility() {
            return "Gerar documentação técnica do que foi implementado (arquitetura, componentes, integrações, operação).";
        }

        @Override
        public String instructions() {
            return """
                    Escreva a documentação TÉCNICA do que foi implementado, em Markdown: visão geral, arquitetura, componentes, integrações,
                    dados, configuração, operação/suporte, decisões técnicas, riscos conhecidos. Baseie-se apenas nos dados.
                    Formato: {"markdown": "..."}
                    """;
        }

        @Override
        public MarkdownOutput mockOutput(DocumentationInput in) {
            TechnicalContext c = in.context();
            return new MarkdownOutput("""
                    # Documentação técnica — %s (%s)

                    > Gerada em modo MOCK a partir dos registros da demanda. Revisar antes de publicar.

                    ## Visão geral
                    %s

                    ## Arquitetura
                    %s

                    ## Requisitos atendidos
                    %s

                    ## Decisões técnicas
                    %s

                    ## Execução
                    %s

                    ## Referências
                    - Jira PMO: %s
                    """.formatted(c.title(), c.protocol(), TechnicalContext.n(c.objective()), TechnicalContext.n(c.architecture()),
                    TechnicalContext.bullets(c.ofType("FUNCTIONAL_REQUIREMENT")), TechnicalContext.bullets(c.decisions()),
                    TechnicalContext.n(c.executionSummary()), TechnicalContext.n(c.jiraKey())));
        }
    }

    @Component
    public static class ClientDocumentationAgent extends DocumentationAgent {
        @Override
        public String name() {
            return "ClientDocumentationAgent";
        }

        @Override
        public String responsibility() {
            return "Gerar documentação para o cliente: o que foi entregue, como usar, benefícios e limitações, em linguagem de negócio.";
        }

        @Override
        public String instructions() {
            return """
                    Escreva a documentação para o CLIENTE (linguagem de negócio, sem jargão técnico), em Markdown: o que foi entregue,
                    como utilizar, benefícios, o que ficou fora do escopo, contatos/suporte. Baseie-se apenas nos dados.
                    Formato: {"markdown": "..."}
                    """;
        }

        @Override
        public MarkdownOutput mockOutput(DocumentationInput in) {
            TechnicalContext c = in.context();
            return new MarkdownOutput("""
                    # %s — o que foi entregue

                    > Gerada em modo MOCK a partir dos registros da demanda. Revisar antes de enviar ao cliente.

                    **Protocolo:** %s

                    ## Objetivo atendido
                    %s

                    ## Benefícios esperados
                    %s

                    ## O que foi entregue
                    %s

                    ## Fora do escopo
                    %s
                    """.formatted(c.title(), c.protocol(), TechnicalContext.n(c.objective()), TechnicalContext.n(c.expectedBenefits()),
                    TechnicalContext.bullets(c.ofType("ACCEPTANCE_CRITERION")), TechnicalContext.n(c.outOfScope())));
        }
    }
}
