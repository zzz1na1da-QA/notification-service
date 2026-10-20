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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "app.kafka.user-events-topic=notification-user-events-it",
                "spring.kafka.consumer.group-id=notification-service-it",
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "spring.kafka.listener.auto-startup=true",
                "spring.kafka.listener.concurrency=1",

                "spring.kafka.producer.key-serializer="
                        + "org.apache.kafka.common.serialization.StringSerializer",
                "spring.kafka.producer.value-serializer="
                        + "org.apache.kafka.common.serialization.StringSerializer",

                "spring.mail.properties.mail.smtp.auth=false",
                "spring.mail.properties.mail.smtp.starttls.enable=false",
                "app.mail.from=no-reply@example.com"
        }
)
@EmbeddedKafka(
        kraft = true,
        partitions = 1,
        topics = "notification-user-events-it",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationKafkaIT {

    private static final String TOPIC = "notification-user-events-it";

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
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @BeforeEach
    void setUp() throws Exception {
        GREEN_MAIL.purgeEmailFromAllMailboxes();

        assertFalse(
                listenerRegistry.getListenerContainers().isEmpty(),
                "Kafka listener не зарегистрирован"
        );

        listenerRegistry.getListenerContainers()
                .forEach(container ->
                        ContainerTestUtils.waitForAssignment(container, 1)
                );
    }

    @AfterAll
    static void stopMailServer() {
        GREEN_MAIL.stop();
    }

    @Test
    void kafkaShouldTriggerAccountCreatedEmail() throws Exception {
        String json = """
                {
                  "operation": "CREATED",
                  "email": "kafka.created@example.com"
                }
                """;

        kafkaTemplate.send(
                TOPIC,
                "kafka.created@example.com",
                json
        ).get(10, TimeUnit.SECONDS);

        assertEmail(
                "kafka.created@example.com",
                "Аккаунт создан",
                "Здравствуйте! Ваш аккаунт на сайте ваш сайт "
                        + "был успешно создан."
        );
    }

    @Test
    void kafkaShouldTriggerAccountDeletedEmail() throws Exception {
        String json = """
                {
                  "operation": "DELETED",
                  "email": "kafka.deleted@example.com"
                }
                """;

        kafkaTemplate.send(
                TOPIC,
                "kafka.deleted@example.com",
                json
        ).get(10, TimeUnit.SECONDS);

        assertEmail(
                "kafka.deleted@example.com",
                "Аккаунт удалён",
                "Здравствуйте! Ваш аккаунт был удалён."
        );
    }

    private void assertEmail(
            String expectedRecipient,
            String expectedSubject,
            String expectedText
    ) throws Exception {

        assertTrue(
                GREEN_MAIL.waitForIncomingEmail(10000, 1),
                "После события Kafka письмо не пришло за 10 секунд"
        );

        MimeMessage[] messages = GREEN_MAIL.getReceivedMessages();

        assertEquals(
                1,
                messages.length,
                "Ожидалось ровно одно письмо"
        );

        MimeMessage message = messages[0];

        assertNotNull(message.getFrom());
        assertEquals(1, message.getFrom().length);

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