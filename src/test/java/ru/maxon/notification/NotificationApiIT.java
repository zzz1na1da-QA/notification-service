package ru.maxon.notification;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
        "app.mail.from=no-reply@example.com"
})
@AutoConfigureMockMvc
class NotificationApiIT {

    private static final GreenMail GREEN_MAIL;

    static {
        GREEN_MAIL = new GreenMail(
                new ServerSetup(
                        0,
                        "127.0.0.1",
                        ServerSetup.PROTOCOL_SMTP
                )
        );

        GREEN_MAIL.start();
    }

    @DynamicPropertySource
    static void configureMail(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add(
                "spring.mail.port",
                () -> GREEN_MAIL.getSmtp().getPort()
        );
    }

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void clearMailbox() throws Exception {
        GREEN_MAIL.purgeEmailFromAllMailboxes();
    }

    @AfterAll
    static void stopMailServer() {
        GREEN_MAIL.stop();
    }

    @Test
    void apiShouldSendAccountCreatedEmail() throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operation": "CREATED",
                                  "email": "created@example.com"
                                }
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertEmail(
                "created@example.com",
                "Аккаунт создан",
                "Здравствуйте! Ваш аккаунт на сайте ваш сайт "
                        + "был успешно создан."
        );
    }

    @Test
    void apiShouldSendAccountDeletedEmail() throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operation": "DELETED",
                                  "email": "deleted@example.com"
                                }
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertEmail(
                "deleted@example.com",
                "Аккаунт удалён",
                "Здравствуйте! Ваш аккаунт был удалён."
        );
    }

    @Test
    void apiShouldRejectInvalidEmailWithoutSendingMail()
            throws Exception {

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operation": "CREATED",
                                  "email": "invalid-email"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(
                0,
                GREEN_MAIL.getReceivedMessages().length
        );
    }

    @Test
    void apiShouldRejectMissingOperationWithoutSendingMail()
            throws Exception {

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "maxim@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(
                0,
                GREEN_MAIL.getReceivedMessages().length
        );
    }

    private void assertEmail(
            String expectedRecipient,
            String expectedSubject,
            String expectedText
    ) throws Exception {

        assertTrue(
                GREEN_MAIL.waitForIncomingEmail(5000, 1),
                "SMTP-сервер не получил письмо за 5 секунд"
        );

        MimeMessage[] messages = GREEN_MAIL.getReceivedMessages();

        assertEquals(
                1,
                messages.length,
                "Ожидалось ровно одно письмо"
        );

        MimeMessage message = messages[0];

        String sender = ((InternetAddress) message.getFrom()[0])
                .getAddress();

        assertNotNull(message.getRecipients(Message.RecipientType.TO));
        assertEquals(
                1,
                message.getRecipients(Message.RecipientType.TO).length
        );

        String recipient = ((InternetAddress) message.getRecipients(
                Message.RecipientType.TO
        )[0]).getAddress();

        String actualText = ((String) message.getContent()).strip();

        assertAll(
                () -> assertEquals(
                        "no-reply@example.com",
                        sender
                ),
                () -> assertEquals(
                        expectedRecipient,
                        recipient
                ),
                () -> assertEquals(
                        expectedSubject,
                        message.getSubject()
                ),
                () -> assertEquals(
                        expectedText,
                        actualText
                )
        );
    }
}