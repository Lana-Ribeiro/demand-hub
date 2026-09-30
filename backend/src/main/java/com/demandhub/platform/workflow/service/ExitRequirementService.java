package com.demandhub.platform.workflow.service;

import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.workflow.domain.WorkflowStage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Avalia os gates de saída de estágio. Os verificadores são resolvidos sob demanda (ObjectProvider): eles pertencem a
 * módulos que, por sua vez, dependem do motor de workflow — a resolução tardia evita o ciclo de construção.
 */
@Service
public class ExitRequirementService {

    private final ObjectProvider<ExitRequirementChecker> provider;
    private volatile Map<String, ExitRequirementChecker> checkers;

    public ExitRequirementService(ObjectProvider<ExitRequirementChecker> provider) {
        this.provider = provider;
    }

    private Map<String, ExitRequirementChecker> checkers() {
        Map<String, ExitRequirementChecker> local = checkers;
        if (local == null) {
            local = provider.orderedStream().collect(Collectors.toMap(ExitRequirementChecker::code, Function.identity()));
            checkers = local;
        }
        return local;
    }

    /** Lista de requisitos não atendidos para sair do estágio (vazia = liberado). */
    public List<String> unmet(Demand demand, WorkflowStage stage) {
        List<String> reasons = new ArrayList<>();
        for (String code : stage.exitRequirementSet()) {
            ExitRequirementChecker checker = checkers().get(code);
            if (checker == null) {
                // Fail-safe: requisito configurado sem implementação bloqueia o avanço.
                reasons.add("Requisito '" + code + "' não possui verificador configurado.");
                continue;
            }
            checker.unmetReason(demand).ifPresent(reasons::add);
        }
        return reasons;
    }

    public boolean isKnown(String code) {
        return checkers().containsKey(code);
    }

    public List<String> knownCodes() {
        return checkers().keySet().stream().sorted().toList();
    }
}
