package com.forumx.notification.delivery.entity;

import com.forumx.common.entity.BaseEntity;
import com.forumx.notification.entity.Notification;
import com.forumx.tenant.entity.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "notification_delivery_attempts", indexes = {
        @Index(name = "idx_nda_tenant", columnList = "tenant_id"),
        @Index(name = "idx_nda_notification", columnList = "notification_id"),
        @Index(name = "idx_nda_status", columnList = "status"),
        @Index(name = "idx_nda_channel", columnList = "channel"),
        @Index(name = "idx_nda_correlation", columnList = "correlation_id"),
        @Index(name = "idx_nda_attempt", columnList = "attempt_number")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDeliveryAttempt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", nullable = true)
    private Notification notification;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 30, nullable = false)
    private DeliveryChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 50)
    private DeliveryProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private DeliveryStatus status;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "correlation_id", length = 64, nullable = false)
    private String correlationId;

    @Column(name = "provider_response", columnDefinition = "TEXT")
    private String providerResponse;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;
}
