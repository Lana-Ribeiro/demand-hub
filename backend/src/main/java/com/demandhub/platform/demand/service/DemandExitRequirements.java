package com.demandhub.platform.demand.service;

import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.domain.InformationRequestStatus;
import com.demandhub.platform.demand.repository.DemandRepositories.InformationRequestRepository;
import com.demandhub.platform.workflow.service.ExitRequirementChecker;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Gates do módulo demand. */
public final class DemandExitRequirements {

    private DemandExitRequirements() {}

    @Component
    public static class TriageFieldsSet implements ExitRequirementChecker {
        @Override
        public String code() {
            return "TRIAGE_FIELDS_SET";
        }

        @Override
        public Optional<String> unmetReason(Demand d) {
            List<String> missing = new ArrayList<>();
            if (d.getProject() == null) missing.add("projeto");
            if (d.getDemandType() == null) missing.add("tipo");
            if (d.getPriority() == null) missing.add("prioridade");
            return missing.isEmpty() ? Optional.empty()
                    : Optional.of("Classificação incompleta: defina " + String.join(", ", missing) + ".");
        }
    }

    @Component
    public static class InformationRequestsAnswered implements ExitRequirementChecker {
        private final InformationRequestRepository requests;

        public InformationRequestsAnswered(InformationRequestRepository requests) {
            this.requests = requests;
        }

        @Override
        public String code() {
            return "INFO_REQUESTS_ANSWERED";
        }

        @Override
        public Optional<String> unmetReason(Demand d) {
            long open = requests.countByDemandIdAndStatus(d.getId(), InformationRequestStatus.OPEN);
            return open == 0 ? Optional.empty() : Optional.of(open + " pendência(s) aguardando resposta do solicitante.");
        }
    }
}
