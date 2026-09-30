package com.demandhub.platform.notification.service;

import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.repository.DemandRepositories.DemandRepository;
import com.demandhub.platform.demand.repository.DemandRepositories.InformationRequestRepository;
import com.demandhub.platform.identity.domain.Role;
import com.demandhub.platform.identity.domain.User;
import com.demandhub.platform.identity.repository.UserRepository;
import com.demandhub.platform.notification.service.NotificationService.Message;
import com.demandhub.platform.shared.events.DomainEvents.ApprovalRequested;
import com.demandhub.platform.shared.events.DomainEvents.DemandStageChanged;
import com.demandhub.platform.shared.events.DomainEvents.DemandSubmitted;
import com.demandhub.platform.shared.events.DomainEvents.ExecutionStatusChanged;
import com.demandhub.platform.shared.events.DomainEvents.InformationProvided;
import com.demandhub.platform.shared.events.DomainEvents.InformationRequested;
import com.demandhub.platform.shared.util.SystemTasks;
import com.demandhub.platform.workflow.domain.StageCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Traduz eventos de domínio em notificações. Nenhum outro componente envia e-mail/Teams. */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);
    private static final Set<String> SILENT_ACTIONS = Set.of("CREATE", "SUBMIT", "ANALYSIS_DONE");

    private final NotificationService notifications;
    private final DemandRepository demands;
    private final InformationRequestRepository infoRequests;
    private final UserRepository users;
    private final SystemTasks system;

    public NotificationEventListener(NotificationService notifications, DemandRepository demands,
                                     InformationRequestRepository infoRequests, UserRepository users, SystemTasks system) {
        this.notifications = notifications;
        this.demands = demands;
        this.infoRequests = infoRequests;
        this.users = users;
        this.system = system;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmitted(DemandSubmitted e) {
        handle(e.demandId(), d -> {
            notifications.notify(new Message("DEMAND_SUBMITTED", "Nova demanda " + d.getProtocol() + ": " + d.getTitle(),
                    "Uma nova demanda foi aberta por " + d.getRequester().getFullName() + " e está em análise.", d.getId()), pmoOf(d), true);
            notifications.notify(new Message("DEMAND_RECEIVED", "Demanda recebida — protocolo " + d.getProtocol(),
                    "Recebemos sua demanda \"" + d.getTitle() + "\". Você será avisado a cada etapa.", d.getId()), List.of(d.getRequester()), false);
            return null;
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStageChanged(DemandStageChanged e) {
        if (e.fromStage() == null || SILENT_ACTIONS.contains(e.action())) {
            if ("ANALYSIS_DONE".equals(e.action())) {
                handle(e.demandId(), d -> {
                    notifications.notify(new Message("TRIAGE_READY", "Demanda pronta para triagem: " + d.getProtocol(),
                            "A análise da IA foi concluída. A demanda aguarda revisão do PMO.", d.getId()), pmoOf(d), false);
                    return null;
                });
            }
            return;
        }
        handle(e.demandId(), d -> {
            List<User> recipients = new ArrayList<>(List.of(d.getRequester()));
            if (d.getOwner() != null) recipients.add(d.getOwner());
            String subject = switch (e.toCategory()) {
                case REJECTED -> "Demanda " + d.getProtocol() + " rejeitada";
                case DONE -> "Demanda " + d.getProtocol() + " concluída";
                default -> "Demanda " + d.getProtocol() + " — nova etapa: " + e.toStageName();
            };
            String body = "Etapa atual: " + e.toStageName() + (e.reason() == null ? "" : "\nMotivo/observação: " + e.reason());
            notifications.notify(new Message(e.toCategory() == StageCategory.REJECTED ? "DEMAND_REJECTED"
                    : e.toCategory() == StageCategory.DONE ? "DEMAND_COMPLETED" : "STAGE_CHANGED", subject, body, d.getId()),
                    recipients, e.toCategory() == StageCategory.DONE);
            if (e.toCategory() == StageCategory.READY) {
                notifications.notify(new Message("READY_FOR_DEVELOPMENT", "Nova demanda na fila de desenvolvimento: " + d.getProtocol(),
                        "A demanda \"" + d.getTitle() + "\" foi liberada para desenvolvimento.", d.getId()),
                        users.findActiveByRole(Role.DEVELOPER), true);
            }
            return null;
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInformationRequested(InformationRequested e) {
        handle(e.demandId(), d -> {
            String question = infoRequests.findById(e.requestId()).map(r -> r.getQuestion()).orElse("");
            notifications.notify(new Message("INFORMATION_REQUESTED", "Pendência na demanda " + d.getProtocol(),
                    "O PMO solicitou informações:\n" + question, d.getId()), List.of(d.getRequester()), false);
            return null;
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInformationProvided(InformationProvided e) {
        handle(e.demandId(), d -> {
            notifications.notify(new Message("INFORMATION_PROVIDED", "Pendência respondida — " + d.getProtocol(),
                    "O solicitante respondeu uma pendência.", d.getId()), d.getOwner() != null ? List.of(d.getOwner()) : pmoOf(d), false);
            return null;
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApprovalRequested(ApprovalRequested e) {
        handle(e.demandId(), d -> {
            notifications.notify(new Message("APPROVAL_REQUIRED", "Aprovação necessária: " + d.getProtocol(),
                    "A demanda \"" + d.getTitle() + "\" aguarda sua aprovação (" + e.ruleName() + ").", d.getId()),
                    users.findActiveByRole(e.approverRole()), false);
            return null;
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExecutionStatusChanged(ExecutionStatusChanged e) {
        handle(e.demandId(), d -> {
            List<User> recipients = new ArrayList<>(List.of(d.getRequester()));
            if (d.getOwner() != null) recipients.add(d.getOwner());
            if ("QA".equals(e.toStatus())) recipients.addAll(users.findActiveByRole(Role.QA));
            notifications.notify(new Message(e.done() ? "EXECUTION_DONE" : "EXECUTION_PROGRESS",
                    "Execução técnica de " + d.getProtocol() + ": " + e.toStatus(),
                    "A execução técnica avançou de " + e.fromStatus() + " para " + e.toStatus() + ".", d.getId()), recipients, false);
            return null;
        });
    }

    private List<User> pmoOf(Demand d) {
        if (d.getProject() != null && d.getProject().getPmo() != null) {
            return List.of(d.getProject().getPmo());
        }
        return users.findActiveByRole(Role.PMO);
    }

    private void handle(UUID demandId, Function<Demand, Void> action) {
        try {
            system.run(() -> action.apply(demands.findById(demandId).orElseThrow()));
        } catch (RuntimeException ex) {
            log.warn("Falha ao gerar notificação da demanda {}: {}", demandId, ex.getMessage());
        }
    }
}
