package com.forumx.notification.email.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.forumx.ForumXApplication;
import com.forumx.notification.email.EmailTemplateType;
import com.forumx.notification.email.config.MailProperties;
import com.forumx.notification.email.dto.EmailNotificationCommand;
import com.forumx.notification.service.NotificationApplicationService;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.forumx.messaging.dto.EventEnvelope;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("dev")
@SpringBootTest(classes = ForumXApplication.class, properties = {"forumx.messaging.enabled=true", "management.health.mail.enabled=false"})
public class EmailIntegrationTest {

    @Autowired
    private NotificationApplicationService notificationApplicationService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private MailProperties mailProperties;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private JavaMailSender mailSender;

    @MockBean
    private com.forumx.websocket.gateway.RealtimeGateway realtimeGateway;

    @MockBean
    private com.forumx.redis.gateway.RedisGateway redisGateway;

    @MockBean
    private com.forumx.security.facade.AuthenticationFacade authenticationFacade;

    @MockBean
    private com.forumx.tenant.resolver.TenantResolver tenantResolver;

    @MockBean
    private com.forumx.messaging.gateway.EventGateway eventGateway;

    @Autowired
    private com.forumx.notification.email.listener.EmailEventSubscriber emailEventSubscriber;

    private Tenant tenant;

    @BeforeEach
    public void setUp() {
        reset(mailSender);
        tenant = tenantRepository.findAll().stream()
                .filter(t -> t.getSlug().equals("default"))
                .findFirst()
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .name("Default")
                        .slug("default")
                        .build()));
    }

    @Test
    public void testEmailFlowEndToEnd() throws Exception {
        // Ensure email is enabled
        mailProperties.setEnabled(true);

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        Map<String, String> model = Map.of("verificationUrl", "http://verify-test-link");

        EmailNotificationCommand command = new EmailNotificationCommand(
                tenant.getId(),
                "integration@forumx.com",
                "integration_user",
                EmailTemplateType.REGISTRATION_VERIFICATION,
                model
        );

        // Execute in transaction to trigger AFTER_COMMIT event publisher
        transactionTemplate.execute(status -> {
            notificationApplicationService.queueEmail(command);
            return null;
        });

        // Capture published EventEnvelope
        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope<EmailNotificationCommand>> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(eventGateway, timeout(5000)).publish(
                eq(com.forumx.messaging.constant.MessagingExchanges.NOTIFICATION_EXCHANGE),
                eq(com.forumx.messaging.constant.MessagingRoutingKeys.EMAIL_SEND),
                envelopeCaptor.capture()
        );

        // Simulate RabbitMQ delivery
        emailEventSubscriber.onEmailSendEvent(envelopeCaptor.getValue());

        // Verify delivery to JavaMailSender
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertNotNull(captor.getValue());
    }

    @Test
    public void testEmailSkippedWhenDisabled() throws Exception {
        // Disable email delivery
        mailProperties.setEnabled(false);

        EmailNotificationCommand command = new EmailNotificationCommand(
                tenant.getId(),
                "skipped@forumx.com",
                "skipped_user",
                EmailTemplateType.GENERIC,
                Map.of("message", "Test")
        );

        transactionTemplate.execute(status -> {
            notificationApplicationService.queueEmail(command);
            return null;
        });

        // Capture published EventEnvelope
        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope<EmailNotificationCommand>> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(eventGateway, timeout(5000)).publish(
                eq(com.forumx.messaging.constant.MessagingExchanges.NOTIFICATION_EXCHANGE),
                eq(com.forumx.messaging.constant.MessagingRoutingKeys.EMAIL_SEND),
                envelopeCaptor.capture()
        );

        // Simulate RabbitMQ delivery
        emailEventSubscriber.onEmailSendEvent(envelopeCaptor.getValue());

        // Verify mailSender is never called
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
