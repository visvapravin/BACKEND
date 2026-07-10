package com.forumx.auth.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import com.forumx.common.entity.BaseEntity;
import com.forumx.tenant.entity.Tenant;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
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
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_tenant_username", columnNames = {"tenant_id", "username"}),
                @UniqueConstraint(name = "uk_users_tenant_email", columnNames = {"tenant_id", "email"})
        },
        indexes = {
                @Index(name = "idx_users_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_users_username", columnList = "username"),
                @Index(name = "idx_users_email", columnList = "email"),
                @Index(name = "idx_users_status", columnList = "status"),
                @Index(name = "idx_users_enabled", columnList = "enabled")
        }
)
public class User extends BaseEntity {

    // ── Tenant ──────────────────────────────────────────────────────────

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_users_tenant"))
    private Tenant tenant;

    // ── Identity ────────────────────────────────────────────────────────

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String username;

    @NotBlank
    @Email
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String email;

    @JsonIgnore
    @NotBlank
    @Size(max = 255)
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Size(max = 30)
    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    // ── Authentication state ────────────────────────────────────────────

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Builder.Default
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @JsonIgnore
    @Builder.Default
    @Column(name = "account_locked", nullable = false)
    private boolean accountLocked = false;

    @JsonIgnore
    @Builder.Default
    @Column(name = "account_expired", nullable = false)
    private boolean accountExpired = false;

    @JsonIgnore
    @Builder.Default
    @Column(name = "credentials_expired", nullable = false)
    private boolean credentialsExpired = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @JsonIgnore
    @Column(name = "last_password_change_at")
    private Instant lastPasswordChangeAt;

    @JsonIgnore
    @Builder.Default
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    // ── Relationships ───────────────────────────────────────────────────

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private UserProfile userProfile;

    @Builder.Default
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private Set<UserRole> userRoles = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private Set<RefreshToken> refreshTokens = new HashSet<>();

    // ── Domain methods ──────────────────────────────────────────────────

    public void activate() {
        this.status = UserStatus.ACTIVE;
        this.enabled = true;
    }

    public void deactivate() {
        this.status = UserStatus.INACTIVE;
        this.enabled = false;
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
        this.enabled = false;
    }

    public void ban() {
        this.status = UserStatus.BANNED;
        this.enabled = false;
        this.accountLocked = true;
    }

    public void lockAccount() {
        this.accountLocked = true;
    }

    public void unlockAccount() {
        this.accountLocked = false;
        this.failedLoginAttempts = 0;
    }

    // ── Enum ────────────────────────────────────────────────────────────

    public enum UserStatus {
        ACTIVE,
        INACTIVE,
        SUSPENDED,
        BANNED,
        PENDING_VERIFICATION
    }
}
