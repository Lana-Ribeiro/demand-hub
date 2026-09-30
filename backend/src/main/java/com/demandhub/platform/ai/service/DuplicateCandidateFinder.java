package com.demandhub.platform.ai.service;

import com.demandhub.platform.ai.agent.intake.DuplicateDetectionAgent.Candidate;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.domain.LifecycleState;
import com.demandhub.platform.demand.repository.DemandRepositories.DemandRepository;
import com.demandhub.platform.shared.util.Texts;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Seleção determinística de candidatos a duplicidade por similaridade lexical (inclui demandas legadas). */
@Service
public class DuplicateCandidateFinder {

    static final double MIN_SCORE = 0.2;
    static final int MAX_CANDIDATES = 5;

    private final DemandRepository demands;

    public DuplicateCandidateFinder(DemandRepository demands) {
        this.demands = demands;
    }

    public List<Candidate> find(Demand demand) {
        Set<String> tokens = Texts.tokens(demand.getTitle() + " " + demand.getObjective());
        return demands.findCandidatesForDuplicate(LifecycleState.DRAFT, demand.getId()).stream()
                .filter(d -> d.getLifecycleState() != LifecycleState.CANCELLED)
                .map(d -> new Candidate(ref(d), d.getTitle(), Texts.truncate(d.getObjective(), 500),
                        Texts.jaccard(tokens, Texts.tokens(d.getTitle() + " " + d.getObjective()))))
                .filter(c -> c.score() >= MIN_SCORE)
                .sorted(Comparator.comparingDouble(Candidate::score).reversed())
                .limit(MAX_CANDIDATES)
                .toList();
    }

    static String ref(Demand d) {
        if (d.getProtocol() != null) return d.getProtocol();
        if (d.getOriginalId() != null) return "LEGADO-" + d.getOriginalId();
        return d.getId().toString();
    }
}
