package com.demandhub.platform.notification.web;

import com.demandhub.platform.notification.domain.Notification;
import com.demandhub.platform.notification.service.NotificationService;
import com.demandhub.platform.shared.security.SecurityUtils;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public List<Notification> inbox() {
        return service.inbox(SecurityUtils.currentUser().id());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread() {
        return Map.of("unread", service.unread(SecurityUtils.currentUser().id()));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable UUID id) {
        service.markRead(SecurityUtils.currentUser().id(), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll() {
        service.markAllRead(SecurityUtils.currentUser().id());
    }
}
