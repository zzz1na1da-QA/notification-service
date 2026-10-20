package ru.maxon.notification.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.maxon.notification.event.UserEvent;
import ru.maxon.notification.service.EmailNotificationService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final EmailNotificationService emailNotificationService;

    public NotificationController(
            EmailNotificationService emailNotificationService
    ) {
        this.emailNotificationService = emailNotificationService;
    }

    @PostMapping
    public ResponseEntity<Void> send(
            @Valid @RequestBody UserEvent request
    ) {
        emailNotificationService.send(request);

        return ResponseEntity.noContent().build();
    }
}