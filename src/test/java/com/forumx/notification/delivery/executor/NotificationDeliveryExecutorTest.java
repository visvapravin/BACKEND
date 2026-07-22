package com.forumx.notification.delivery.executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.forumx.auth.entity.User;
import com.forumx.notification.delivery.domain.DeliveryExecutionResult;
import com.forumx.notification.delivery.domain.NotificationDeliveryContext;
import com.forumx.notification.delivery.entity.DeliveryChannel;
import com.forumx.notification.delivery.entity.DeliveryProvider;
import com.forumx.notification.delivery.entity.DeliveryStatus;
import com.forumx.notification.delivery.entity.NotificationDeliveryAttempt;
import com.forumx.notification.delivery.exception.RetryableDeliveryException;
import com.forumx.notification.delivery.metrics.NotificationDeliveryMetricsService;
import com.forumx.notification.delivery.policy.ConfigurableRetryPolicy;
import com.forumx.notification.delivery.policy.RetryPolicyResolver;
import com.forumx.notification.delivery.strategy.DeliveryStrategy;
import com.forumx.notification.delivery.strategy.DeliveryStrategyRegistry;
import com.forumx.notification.delivery.util.CorrelationIdGenerator;
import com.forumx.tenant.entity.Tenant;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class NotificationDeliveryExecutorTest {

    @Mock private DeliveryStrategyRegistry strategyRegistry;
    @Mock private RetryPolicyResolver retryPolicyResolver;
    @Mock private AttemptRecorder attemptRecorder;
    @Mock private RetryCoordinator retryCoordinator;
    @Mock private NotificationDeliveryMetricsService metricsService;
    @Mock private CorrelationIdGenerator correlationIdGenerator;
    @Mock private DeliveryStrategy deliveryStrategy;
    @Mock private ConfigurableRetryPolicy retryPolicy;

    private NotificationDeliveryExecutor executor;
    private Tenant tenant;
    private User recipient;

    @BeforeEach
    public void setUp() {
        executor = new NotificationDeliveryExecutor(
                strategyRegistry,
                retryPolicyResolver,
                attemptRecorder,
                retryCoordinator,
                metricsService,
                correlationIdGenerator
        );

        tenant = Tenant.builder().id(1L).slug("demo").name("Demo").build();
        recipient = User.builder().id(10L).username("alice").email("alice@example.com").tenant(tenant).build();
    }

    @Test
    public void testExecuteSuccess() {
        NotificationDeliveryContext context = new NotificationDeliveryContext(
                null, recipient, tenant, null, DeliveryChannel.EMAIL, DeliveryProvider.SMTP,
                1, "DECISION", "corr-123", null, null, java.util.Map.of()
        );

        NotificationDeliveryAttempt attempt = NotificationDeliveryAttempt.builder()
                .id(100L).tenant(tenant).channel(DeliveryChannel.EMAIL).provider(DeliveryProvider.SMTP)
                .status(DeliveryStatus.PROCESSING).attemptNumber(1).correlationId("corr-123").build();

        when(strategyRegistry.getStrategy(DeliveryChannel.EMAIL)).thenReturn(deliveryStrategy);
        when(retryPolicyResolver.resolvePolicy(DeliveryChannel.EMAIL)).thenReturn(retryPolicy);
        when(attemptRecorder.recordStart(any(), any())).thenReturn(attempt);
        when(deliveryStrategy.deliver(any())).thenReturn(DeliveryExecutionResult.success("SMTP OK", Duration.ofMillis(50)));

        NotificationDeliveryAttempt result = executor.execute(context);

        assertNotNull(result);
        verify(attemptRecorder).recordSuccess(eq(attempt), eq("SMTP OK"), any(Instant.class), any(Duration.class));
        verify(metricsService).recordSuccess(eq(DeliveryChannel.EMAIL), eq(DeliveryProvider.SMTP), eq("demo"), any(Duration.class));
    }

    @Test
    public void testExecuteRetryableFailureSchedulesRetry() {
        NotificationDeliveryContext context = new NotificationDeliveryContext(
                null, recipient, tenant, null, DeliveryChannel.EMAIL, DeliveryProvider.SMTP,
                1, "DECISION", "corr-123", null, null, java.util.Map.of()
        );

        NotificationDeliveryAttempt attempt = NotificationDeliveryAttempt.builder()
                .id(100L).tenant(tenant).channel(DeliveryChannel.EMAIL).provider(DeliveryProvider.SMTP)
                .status(DeliveryStatus.PROCESSING).attemptNumber(1).correlationId("corr-123").build();

        when(strategyRegistry.getStrategy(DeliveryChannel.EMAIL)).thenReturn(deliveryStrategy);
        when(retryPolicyResolver.resolvePolicy(DeliveryChannel.EMAIL)).thenReturn(retryPolicy);
        when(attemptRecorder.recordStart(any(), any())).thenReturn(attempt);
        when(deliveryStrategy.deliver(any())).thenThrow(new RetryableDeliveryException("Connection timeout"));
        when(retryPolicy.shouldRetry(eq(1), any())).thenReturn(true);
        when(retryPolicy.getNextRetryDelay(1)).thenReturn(Duration.ofSeconds(30));

        executor.execute(context);

        verify(attemptRecorder).recordRetry(eq(attempt), eq("Connection timeout"), any(), any(), any());
        verify(metricsService).recordRetry(DeliveryChannel.EMAIL, DeliveryProvider.SMTP, "demo");
        verify(retryCoordinator).scheduleRetry(any(), eq(Duration.ofSeconds(30)));
    }
}
