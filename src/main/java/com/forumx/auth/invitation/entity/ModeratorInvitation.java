package com.forumx.auth.invitation.entity;

import java.time.Instant;

import com.forumx.auth.entity.User;
import com.forumx.auth.enums.RoleType;
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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "moderator_invitations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_moderator_invitations_token_hash", columnNames = "token_hash")
        },
        indexes = {
                @Index(name = "idx_moderator_invitations_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_moderator_invitations_token_hash", columnList = "token_hash"),
                @Index(name = "idx_moderator_invitations_tenant_status", columnList = "tenant_id, status"),
                @Index(name = "idx_moderator_invitations_tenant_email", columnList = "tenant_id, email"),
                @Index(name = "idx_moderator_invitations_expires_at", columnList = "expires_at")
        }
)
public class ModeratorInvitation extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_moderator_invitations_tenant"))
    private Tenant tenant;

    @NotBlank
    @Email
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String email;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RoleType role;

    @NotBlank
    @Size(max = 255)
    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_moderator_invitations_invited_by"))
    private User invitedBy;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public boolean isExpired() {
        return Instant.now().isAfter(this.expiresAt);
    }

    public boolean isPending() {
        return this.status == InvitationStatus.PENDING;
    }
}
