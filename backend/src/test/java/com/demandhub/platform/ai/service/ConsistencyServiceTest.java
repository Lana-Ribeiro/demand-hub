package com.demandhub.platform.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.demandhub.platform.demand.domain.DemandField;
import org.junit.jupiter.api.Test;

class ConsistencyServiceTest {

    @Test
    void datesAreComparedByMonth() {
        assertThat(ConsistencyService.equivalent(DemandField.DESIRED_DATE, "2026-10-01", "2026-10-31")).isTrue();
        assertThat(ConsistencyService.equivalent(DemandField.DESIRED_DATE, "2026-10-01", "2026-12-15")).isFalse();
    }

    @Test
    void numbersIgnoreFormatting() {
        assertThat(ConsistencyService.equivalent(DemandField.ESTIMATED_BUDGET, "1500000.00", "1.500.000,00")).isTrue();
        assertThat(ConsistencyService.equivalent(DemandField.ESTIMATED_BUDGET, "1500000", "900000")).isFalse();
    }

    @Test
    void textsToleratePhrasingDifferences() {
        assertThat(ConsistencyService.equivalent(DemandField.SPONSOR_NAME, "Maria Patrocinadora", "maria patrocinadora")).isTrue();
        assertThat(ConsistencyService.equivalent(DemandField.OBJECTIVE, "Automatizar inventário", "Automatizar o inventário de rede")).isTrue();
        assertThat(ConsistencyService.equivalent(DemandField.OBJECTIVE, "Reduzir custos de energia", "Criar portal de atendimento")).isFalse();
    }
}
