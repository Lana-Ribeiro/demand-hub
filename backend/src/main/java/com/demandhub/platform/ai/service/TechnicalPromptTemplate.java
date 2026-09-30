package com.demandhub.platform.ai.service;

import com.demandhub.platform.ai.agent.technical.TechnicalAgents.PromptEngineerAgent;
import com.demandhub.platform.ai.agent.technical.TechnicalContext;
import com.demandhub.platform.shared.util.Texts;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Estrutura determinística do prompt técnico (Estratégia A) para Claude, Claude Code, Copilot etc.
 * Seções fixas garantem que nenhum item obrigatório seja omitido; lacunas aparecem como "A definir".
 */
final class TechnicalPromptTemplate {

    private TechnicalPromptTemplate() {}

    static String render(TechnicalContext c, String specification, PromptEngineerAgent.Output guidance) {
        return """
                # Prompt técnico — %s (%s)

                Você é um engenheiro de software sênior. Implemente a demanda abaixo seguindo os padrões do repositório.
                Antes de codificar, leia o código existente relacionado. Pergunte quando uma informação obrigatória estiver "A definir".

                ## 1. Contexto
                %s

                ## 2. Objetivo
                %s

                ## 3. Escopo
                %s

                **Fora do escopo:** %s

                ## 4. Requisitos funcionais
                %s

                ## 5. Requisitos não funcionais
                %s

                ## 6. Regras de negócio e decisões do refinamento
                %s

                ## 7. Arquitetura
                %s

                ## 8. Tecnologias, integrações e APIs
                Sistemas envolvidos: %s
                Dependências:
                %s

                ## 9. Arquivos/áreas provavelmente afetados
                %s

                ## 10. Banco de dados
                Use migrations versionadas. Modelagem: A definir a partir dos requisitos.

                ## 11. Passos sugeridos
                %s

                ## 12. Critérios de aceite
                %s

                ## 13. Testes
                %s

                ## 14. Restrições
                - Não incluir credenciais no código; usar variáveis de ambiente/secrets.
                - Não alterar comportamento fora do escopo sem aprovação.
                - Toda mudança deve ter testes automatizados e passar por code review.
                - Riscos conhecidos:
                %s

                ## 15. Referências
                - Jira PMO: %s
                %s
                """.formatted(c.title(), c.protocol(), def(c.problem()), def(c.objective()), def(c.scope()), def(c.outOfScope()),
                list(c.ofType("FUNCTIONAL_REQUIREMENT")), list(c.ofType("NON_FUNCTIONAL_REQUIREMENT")),
                c.decisions().isEmpty() ? "- A definir" : TechnicalContextBullets.of(c.decisions()),
                def(c.architecture()), def(c.systems()), list(c.ofType("DEPENDENCY")),
                strings(guidance == null ? List.of() : guidance.affectedAreas()),
                strings(guidance == null ? List.of() : guidance.implementationSteps()),
                list(c.ofType("ACCEPTANCE_CRITERION")),
                strings(guidance == null ? List.of() : guidance.testScenarios()),
                list(c.ofType("RISK")), def(c.jiraKey()),
                specification == null ? "" : "\n## Anexo — Especificação técnica\n\n" + specification);
    }

    private static String def(String s) {
        return Texts.isBlank(s) ? "A definir" : s;
    }

    private static String list(List<TechnicalContext.Item> items) {
        return items.isEmpty() ? "- A definir" : items.stream().map(i -> "- " + i.description()).collect(Collectors.joining("\n"));
    }

    private static String strings(List<String> items) {
        return items.isEmpty() ? "- A definir" : items.stream().map(s -> "- " + s).collect(Collectors.joining("\n"));
    }

    private static final class TechnicalContextBullets {
        static String of(List<TechnicalContext.Item> items) {
            return items.stream().map(i -> "- [" + i.type() + "] " + i.description()).collect(Collectors.joining("\n"));
        }
    }
}
