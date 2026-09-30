package com.demandhub.platform.ai.agent.technical;

import com.demandhub.platform.shared.util.Texts;
import java.util.List;
import java.util.stream.Collectors;

/** Contexto mínimo para agentes técnicos (sem comentários, credenciais ou dados de outras demandas). */
public record TechnicalContext(String protocol, String title, String demandType, String project, String objective,
                               String problem, String expectedBenefits, String scope, String outOfScope, String systems,
                               List<Item> requirements, List<Item> decisions, String architecture, String jiraKey,
                               String executionSummary) {

    public record Item(String type, String description) {}

    public List<Item> ofType(String type) {
        return requirements.stream().filter(i -> i.type().equals(type)).toList();
    }

    public String asText() {
        return """
                Protocolo: %s
                Título: %s
                Tipo: %s | Projeto: %s
                Objetivo: %s
                Problema: %s
                Benefícios esperados: %s
                Escopo: %s
                Fora do escopo: %s
                Sistemas envolvidos: %s
                Requisitos e critérios:
                %s
                Decisões:
                %s
                Arquitetura aprovada/proposta:
                %s
                """.formatted(protocol, title, demandType, project, n(objective), n(problem), n(expectedBenefits), n(scope),
                n(outOfScope), n(systems), bullets(requirements), bullets(decisions), n(architecture));
    }

    static String bullets(List<Item> items) {
        return items.isEmpty() ? "- (nenhum registrado)"
                : items.stream().map(i -> "- [" + i.type() + "] " + i.description()).collect(Collectors.joining("\n"));
    }

    static String n(String s) {
        return Texts.isBlank(s) ? "Não informado" : s;
    }
}
