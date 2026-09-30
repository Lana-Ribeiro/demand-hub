package com.demandhub.platform.dashboard.service;

import com.demandhub.platform.approval.domain.Approval;
import com.demandhub.platform.approval.repository.ApprovalRepositories.ApprovalRepository;
import com.demandhub.platform.config.AppProperties;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.domain.DemandStageHistory;
import com.demandhub.platform.demand.domain.LifecycleState;
import com.demandhub.platform.demand.repository.DemandRepositories.DemandRepository;
import com.demandhub.platform.demand.repository.DemandRepositories.DemandStageHistoryRepository;
import com.demandhub.platform.execution.repository.ExecutionRepositories.TechnicalExecutionRepository;
import com.demandhub.platform.workflow.domain.StageCategory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indicadores gerenciais calculados exclusivamente a partir dos dados registrados.
 * Tempos médios vêm do histórico real de estágios; sem amostra suficiente → {@code sufficient=false} ("Dados insuficientes").
 */
@Service
public class DashboardService {

    private static final Set<LifecycleState> OPEN = EnumSet.of(LifecycleState.ACTIVE, LifecycleState.ON_HOLD);

    private final DemandRepository demands;
    private final DemandStageHistoryRepository history;
    private final ApprovalRepository approvals;
    private final TechnicalExecutionRepository executions;
    private final AppProperties props;
    private final Clock clock;

    public DashboardService(DemandRepository demands, DemandStageHistoryRepository history, ApprovalRepository approvals,
                            TechnicalExecutionRepository executions, AppProperties props, Clock clock) {
        this.demands = demands;
        this.history = history;
        this.approvals = approvals;
        this.executions = executions;
        this.props = props;
        this.clock = clock;
    }

    public record Count(String key, String label, long count, String color) {}

    public record TimeMetric(String label, Double averageHours, int sampleSize, boolean sufficient) {}

    public record StalledDemand(UUID id, String protocol, String title, String stage, long daysInStage) {}

    public record Dashboard(long openDemands, long completedDemands, long rejectedDemands, long pendingApprovals,
                            long inRefinement, long inDevelopment, long inQa, List<Count> byStage, List<Count> byProject,
                            List<Count> byType, List<Count> byPriority, List<StalledDemand> stalled, int stalledThresholdDays,
                            List<TimeMetric> averageTimes, List<Count> volumeByMonth, Instant generatedAt) {}

    @Transactional(readOnly = true)
    public Dashboard build(Long projectId) {
        List<Demand> all = demands.findByLifecycleStates(EnumSet.of(LifecycleState.ACTIVE, LifecycleState.ON_HOLD,
                        LifecycleState.COMPLETED, LifecycleState.REJECTED, LifecycleState.CANCELLED)).stream()
                .filter(d -> d.getSubmittedAt() != null)
                .filter(d -> projectId == null || (d.getProject() != null && projectId.equals(d.getProject().getId())))
                .toList();
        Set<UUID> ids = all.stream().map(Demand::getId).collect(Collectors.toSet());
        List<Demand> open = all.stream().filter(d -> OPEN.contains(d.getLifecycleState())).toList();

        long pending = approvals.findAll().stream()
                .filter(a -> a.getStatus() == Approval.Status.PENDING && ids.contains(a.getDemandId())).count();
        long inQa = executions.findByDemandIdIn(ids).stream()
                .filter(e -> "QA".equals(e.getStatusCode()) && e.getCompletedAt() == null).count();

        int threshold = props.dashboard().stalledDays();
        Instant now = clock.instant();
        List<StalledDemand> stalled = open.stream()
                .filter(d -> d.getStageEnteredAt() != null && Duration.between(d.getStageEnteredAt(), now).toDays() >= threshold)
                .sorted(Comparator.comparing(Demand::getStageEnteredAt))
                .limit(15)
                .map(d -> new StalledDemand(d.getId(), d.getProtocol(), d.getTitle(),
                        d.getCurrentStage() == null ? "-" : d.getCurrentStage().getName(),
                        ChronoUnit.DAYS.between(d.getStageEnteredAt(), now)))
                .toList();

        List<DemandStageHistory> closedHistory = history.findAll().stream()
                .filter(h -> h.getExitedAt() != null && ids.contains(h.getDemandId())).toList();

        return new Dashboard(open.size(),
                all.stream().filter(d -> d.getLifecycleState() == LifecycleState.COMPLETED).count(),
                all.stream().filter(d -> d.getLifecycleState() == LifecycleState.REJECTED).count(),
                pending,
                countCategory(open, StageCategory.REFINEMENT),
                countCategory(open, StageCategory.EXECUTION),
                inQa,
                group(open, d -> d.getCurrentStage() == null ? "-" : d.getCurrentStage().getCategory().name(),
                        d -> d.getCurrentStage() == null ? "-" : categoryLabel(d.getCurrentStage().getCategory()), d -> null),
                group(open, d -> d.getProject() == null ? "SEM_PROJETO" : d.getProject().getCode(),
                        d -> d.getProject() == null ? "Sem projeto definido" : d.getProject().getCode() + " — " + d.getProject().getName(),
                        d -> d.getProject() == null ? null : d.getProject().getColor()),
                group(open, d -> d.getDemandType() == null ? "SEM_TIPO" : d.getDemandType().getCode(),
                        d -> d.getDemandType() == null ? "Sem tipo" : d.getDemandType().getName(), d -> null),
                group(open, d -> d.getPriority() == null ? "SEM_PRIORIDADE" : d.getPriority().getCode(),
                        d -> d.getPriority() == null ? "Sem prioridade" : d.getPriority().getCode() + " — " + d.getPriority().getName(),
                        d -> d.getPriority() == null ? null : d.getPriority().getColor()),
                stalled, threshold,
                List.of(average("Tempo médio de triagem", closedHistory, StageCategory.TRIAGE),
                        average("Tempo médio de refinamento", closedHistory, StageCategory.REFINEMENT),
                        average("Tempo médio de execução", closedHistory, StageCategory.EXECUTION)),
                volume(all), now);
    }

    private static long countCategory(List<Demand> list, StageCategory c) {
        return list.stream().filter(d -> d.getCurrentStage() != null && d.getCurrentStage().getCategory() == c).count();
    }

    private static List<Count> group(List<Demand> list, Function<Demand, String> key, Function<Demand, String> label,
                                     Function<Demand, String> color) {
        Map<String, Count> map = new LinkedHashMap<>();
        for (Demand d : list) {
            String k = key.apply(d);
            Count c = map.get(k);
            map.put(k, new Count(k, label.apply(d), c == null ? 1 : c.count() + 1, color.apply(d)));
        }
        return map.values().stream().sorted(Comparator.comparingLong(Count::count).reversed()).toList();
    }

    private TimeMetric average(String label, List<DemandStageHistory> closed, StageCategory category) {
        List<Long> minutes = closed.stream().filter(h -> h.getCategory() == category)
                .map(h -> Duration.between(h.getEnteredAt(), h.getExitedAt()).toMinutes()).toList();
        boolean sufficient = minutes.size() >= Math.max(1, props.dashboard().minSampleSize());
        Double avg = sufficient ? Math.round(minutes.stream().mapToLong(Long::longValue).average().orElse(0) / 0.6) / 100.0 : null;
        return new TimeMetric(label, avg, minutes.size(), sufficient);
    }

    private List<Count> volume(List<Demand> all) {
        YearMonth current = YearMonth.now(clock.withZone(ZoneOffset.UTC));
        Map<YearMonth, Long> byMonth = new TreeMap<>();
        for (int i = 11; i >= 0; i--) {
            byMonth.put(current.minusMonths(i), 0L);
        }
        all.forEach(d -> byMonth.computeIfPresent(YearMonth.from(d.getSubmittedAt().atZone(ZoneOffset.UTC)), (k, v) -> v + 1));
        return byMonth.entrySet().stream().map(e -> new Count(e.getKey().toString(), e.getKey().toString(), e.getValue(), null)).toList();
    }

    static String categoryLabel(StageCategory c) {
        return switch (c) {
            case INTAKE -> "Análise da IA";
            case TRIAGE -> "Triagem PMO";
            case ON_HOLD -> "Aguardando informação";
            case APPROVAL -> "Aprovações";
            case ANALYSIS -> "Análise de capacidade";
            case REFINEMENT -> "Refinamento";
            case ARCHITECTURE -> "Arquitetura";
            case READY -> "Pronta p/ desenvolvimento";
            case EXECUTION -> "Em execução";
            case DOCUMENTATION -> "Documentação";
            default -> c.name();
        };
    }

}
