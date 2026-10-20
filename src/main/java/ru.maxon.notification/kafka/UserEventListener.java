package ru.maxon.notification.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.maxon.notification.event.UserEvent;
import ru.maxon.notification.service.EmailNotificationService;

@Component
public class UserEventListener {

    private static final Logger log =
            LoggerFactory.getLogger(UserEventListener.class);

    private final EmailNotificationService emailNotificationService;

    public UserEventListener(
            EmailNotificationService emailNotificationService
    ) {
        this.emailNotificationService = emailNotificationService;
    }

    @KafkaListener(topics = "${app.kafka.user-events-topic}")
    public void handle(UserEvent event) {
        log.info(
                "Получено событие из Kafka: operation={}",
                event.operation()
        );

        emailNotificationService.send(event);
    }
}