package com.demandhub.platform.ai.agent.intake;

import static org.assertj.core.api.Assertions.assertThat;

import com.demandhub.platform.ai.agent.AgentSupport.FieldSuggestion;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtractionHeuristicsTest {

    @Test
    void parsesPortugueseDates() {
        assertThat(ExtractionAgents.HeuristicExtractor.parseDate("Outubro/2026")).isEqualTo("2026-10-01");
        assertThat(ExtractionAgents.HeuristicExtractor.parseDate("15/12/2026")).isEqualTo("2026-12-15");
        assertThat(ExtractionAgents.HeuristicExtractor.parseDate("03/2027")).isEqualTo("2027-03-01");
        assertThat(ExtractionAgents.HeuristicExtractor.parseDate("dezembro de 2026")).isEqualTo("2026-12-01");
        assertThat(ExtractionAgents.HeuristicExtractor.parseDate("sem data")).isNull();
    }

    @Test
    void mapsLabelledLinesToFields() {
        List<FieldSuggestion> s = ExtractionAgents.HeuristicExtractor.extract("""
                Título: Portal de inventário
                Objetivo: Centralizar o inventário
                Fora do escopo: Integração com faturamento
                Escopo: Equipamentos ópticos
                Orçamento: R$ 250.000,00
                Texto livre sem rótulo
                """);
        assertThat(s).extracting(FieldSuggestion::field)
                .containsExactly("title", "objective", "outOfScope", "scopeDescription", "estimatedBudget");
        assertThat(s.get(4).value()).isEqualTo("250.000,00");
    }

    @Test
    void classificationHeuristicDistinguishesNonTechnicalDemands() {
        assertThat(ClassificationAgent.guessType("Alocação de dois analistas")).isEqualTo("RESOURCE_ALLOCATION");
        assertThat(ClassificationAgent.guessType("Mapeamento de processos da operação")).isEqualTo("CONSULTING");
        assertThat(ClassificationAgent.guessType("Integração entre o CRM e o ERP")).isEqualTo("INTEGRATION");
        assertThat(ClassificationAgent.guessType("Automatizar o processo de inventário")).isEqualTo("AUTOMATION");
    }
}
