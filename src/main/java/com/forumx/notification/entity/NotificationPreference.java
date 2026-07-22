package com.forumx.notification.entity;

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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@DynamicInsert
@DynamicUpdate
@Entity
@Table(
        name = "notification_preferences",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_notification_type", columnNames = {"user_id", "notification_type"})
        },
        indexes = {
                @Index(name = "idx_notif_pref_tenant", columnList = "tenant_id"),
                @Index(name = "idx_notif_pref_user", columnList = "user_id"),
                @Index(name = "idx_notif_pref_type", columnList = "notification_type")
        }
)
public class NotificationPreference extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notif_pref_tenant"))
    private Tenant tenant;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notif_pref_user"))
    private User user;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    @Column(name = "websocket_enabled", nullable = false)
    private boolean webSocketEnabled = true;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = false;

    @Column(name = "digest_enabled", nullable = false)
    private boolean digestEnabled = false;
}
