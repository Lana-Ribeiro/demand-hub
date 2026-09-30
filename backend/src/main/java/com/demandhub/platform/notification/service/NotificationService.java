package com.demandhub.platform.notification.service;

import com.demandhub.platform.config.AppProperties;
import com.demandhub.platform.identity.domain.User;
import com.demandhub.platform.notification.domain.Notification;
import com.demandhub.platform.notification.repository.NotificationRepository;
import com.demandhub.platform.shared.error.ApiException;
import com.demandhub.platform.shared.util.Texts;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * Serviço central de notificações — único ponto de envio de e-mail (Outlook/SMTP) e Teams na aplicação.
 * Cada tentativa é registrada; canais desabilitados geram registro SKIPPED (rastreável, sem falha silenciosa).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final AppProperties props;
    private final Clock clock;
    private final RestClient http = RestClient.create();

    public NotificationService(NotificationRepository repository, ObjectProvider<JavaMailSender> mailSender,
                               AppProperties props, Clock clock) {
        this.repository = repository;
        this.mailSender = mailSender;
        this.props = props;
        this.clock = clock;
    }

    public record Message(String eventType, String subject, String body, UUID demandId) {}

    /** Notifica destinatários (in-app + e-mail) e, opcionalmente, o canal do Teams. */
    @Transactional
    public void notify(Message message, Collection<User> recipients, boolean teams) {
        Map<UUID, User> unique = new LinkedHashMap<>();
        recipients.stream().filter(u -> u != null && u.isActive()).forEach(u -> unique.putIfAbsent(u.getId(), u));
        for (User u : unique.values()) {
            record(u.getId(), Notification.Channel.IN_APP, message, Notification.Status.SENT, null);
            sendEmail(u, message);
        }
        if (teams) {
            sendTeams(message);
        }
    }

    private void sendEmail(User to, Message message) {
        AppProperties.Notifications.Email cfg = props.notifications().email();
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!cfg.enabled() || sender == null || Texts.isBlank(cfg.from())) {
            record(to.getId(), Notification.Channel.EMAIL, message, Notification.Status.SKIPPED, "Canal de e-mail desabilitado.");
            return;
        }
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(cfg.from());
            mail.setTo(to.getEmail());
            mail.setSubject("[Demand Hub] " + message.subject());
            mail.setText(message.body() + link(message.demandId()));
            sender.send(mail);
            record(to.getId(), Notification.Channel.EMAIL, message, Notification.Status.SENT, null);
        } catch (RuntimeException e) {
            log.warn("Falha ao enviar e-mail ({}): {}", message.eventType(), e.getClass().getSimpleName());
            record(to.getId(), Notification.Channel.EMAIL, message, Notification.Status.FAILED, e.getClass().getSimpleName());
        }
    }

    private void sendTeams(Message message) {
        AppProperties.Notifications.Teams cfg = props.notifications().teams();
        if (!cfg.enabled() || Texts.isBlank(cfg.webhookUrl())) {
            record(null, Notification.Channel.TEAMS, message, Notification.Status.SKIPPED, "Canal do Teams desabilitado.");
            return;
        }
        try {
            http.post().uri(cfg.webhookUrl()).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", "**" + message.subject() + "**\n\n" + message.body() + link(message.demandId())))
                    .retrieve().toBodilessEntity();
            record(null, Notification.Channel.TEAMS, message, Notification.Status.SENT, null);
        } catch (RuntimeException e) {
            log.warn("Falha ao enviar mensagem ao Teams ({}): {}", message.eventType(), e.getClass().getSimpleName());
            record(null, Notification.Channel.TEAMS, message, Notification.Status.FAILED, e.getClass().getSimpleName());
        }
    }

    private String link(UUID demandId) {
        return demandId == null ? "" : "\n\nAcesse: " + props.publicUrl() + "/demands/" + demandId;
    }

    private void record(UUID recipientId, Notification.Channel channel, Message message, Notification.Status status, String error) {
        Notification n = new Notification();
        n.setRecipientId(recipientId);
        n.setChannel(channel);
        n.setEventType(message.eventType());
        n.setSubject(Texts.truncate(message.subject(), 300));
        n.setBody(Texts.truncate(message.body(), 8000));
        n.setDemandId(message.demandId());
        n.setStatus(status);
        n.setError(error);
        n.setCreatedAt(clock.instant());
        repository.save(n);
    }

    // ------------------------------------------------------------ caixa de entrada in-app

    @Transactional(readOnly = true)
    public List<Notification> inbox(UUID userId) {
        return repository.findTop50ByRecipientIdAndChannelOrderByCreatedAtDesc(userId, Notification.Channel.IN_APP);
    }

    @Transactional(readOnly = true)
    public long unread(UUID userId) {
        return repository.countByRecipientIdAndChannelAndReadAtIsNull(userId, Notification.Channel.IN_APP);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification n = repository.findById(notificationId).filter(x -> userId.equals(x.getRecipientId()))
                .orElseThrow(() -> ApiException.notFound("Notificação", notificationId));
        if (n.getReadAt() == null) {
            n.setReadAt(clock.instant());
        }
    }

    @Transactional
    public void markAllRead(UUID userId) {
        repository.findTop50ByRecipientIdAndChannelOrderByCreatedAtDesc(userId, Notification.Channel.IN_APP).stream()
                .filter(n -> n.getReadAt() == null).forEach(n -> n.setReadAt(clock.instant()));
    }
}
