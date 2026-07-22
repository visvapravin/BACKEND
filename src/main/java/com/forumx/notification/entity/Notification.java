package com.forumx.notification.entity;

import java.time.Instant;

import com.forumx.auth.entity.User;
import com.forumx.common.entity.BaseEntity;
import com.forumx.tenant.entity.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/** A tenant-scoped notification persisted for one recipient. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@DynamicInsert
@DynamicUpdate
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_tenant", columnList = "tenant_id"),
        @Index(name = "idx_notifications_recipient", columnList = "recipient_id"),
        @Index(name = "idx_notifications_is_read", columnList = "is_read"),
        @Index(name = "idx_notifications_type", columnList = "notification_type"),
        @Index(name = "idx_notifications_reference", columnList = "reference_type, reference_id")
})
public class Notification extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notifications_tenant"))
    private Tenant tenant;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notifications_recipient"))
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", foreignKey = @ForeignKey(name = "fk_notifications_actor"))
    private User actor;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String title;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 50)
    private ReferenceType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private Instant readAt;
}
