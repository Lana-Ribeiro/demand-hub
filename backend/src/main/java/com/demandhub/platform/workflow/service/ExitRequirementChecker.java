package com.demandhub.platform.workflow.service;

import com.demandhub.platform.demand.domain.Demand;
import java.util.Optional;

/**
 * Gate determinístico de saída de estágio (Strategy). Cada módulo contribui com os seus.
 * Retorna a mensagem do requisito não atendido, ou vazio quando atendido.
 */
public interface ExitRequirementChecker {

    String code();

    Optional<String> unmetReason(Demand demand);
}
