package ru.maxon.notification.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import ru.maxon.notification.event.UserEvent;

@Service
@Validated
public class EmailNotificationService {

    private static final Logger log =
            LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public EmailNotificationService(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String from
    ) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(@NotNull @Valid UserEvent event) {
        String subject;
        String text;

        switch (event.operation()) {
            case CREATED -> {
                subject = "Аккаунт создан";
                text = "Здравствуйте! Ваш аккаунт на сайте ваш сайт "
                        + "был успешно создан.";
            }
            case DELETED -> {
                subject = "Аккаунт удалён";
                text = "Здравствуйте! Ваш аккаунт был удалён.";
            }
            default -> throw new IllegalArgumentException(
                    "Неизвестная операция: " + event.operation()
            );
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(event.email());
        message.setSubject(subject);
        message.setText(text);

        mailSender.send(message);

        log.info(
                "SMTP-сервер принял уведомление об операции {}",
                event.operation()
        );
    }
}