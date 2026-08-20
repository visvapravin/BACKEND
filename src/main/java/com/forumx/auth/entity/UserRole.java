package com.forumx.auth.entity;

import java.time.Instant;

import com.forumx.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Represents the assignment of a Role to a User.
 * Replaces the traditional Many-To-Many relationship to support auditing, expiration, and future extensibility.
 */
import com.forumx.tenant.entity.Tenant;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(exclude = {"user", "role", "tenant"})
@DynamicInsert
@DynamicUpdate
@Entity
@Table(
        name = "user_roles",
        indexes = {
                @Index(
                        name = "idx_user_roles_user",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_user_roles_tenant",
                        columnList = "tenant_id"
                ),
                @Index(
                        name = "idx_user_roles_role",
                        columnList = "role_id"
                ),
                @Index(
                        name = "idx_user_roles_user_tenant",
                        columnList = "user_id,tenant_id"
                ),
                @Index(
                        name = "idx_user_roles_user_tenant_active",
                        columnList = "user_id,tenant_id,active"
                ),
                @Index(
                        name = "idx_user_roles_active",
                        columnList = "active"
                ),
                @Index(
                        name = "idx_user_roles_expires_at",
                        columnList = "expires_at"
                )
        }
)
public class UserRole extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            foreignKey = @ForeignKey(name = "fk_user_roles_user")
    )
    private User user;

    /** Null only for global/platform-scoped roles such as PLATFORM_ADMIN. */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(
            name = "tenant_id",
            nullable = true,
            foreignKey = @ForeignKey(name = "fk_user_roles_tenant")
    )
    private Tenant tenant;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "role_id",
            foreignKey = @ForeignKey(name = "fk_user_roles_role")
    )
    private Role role;

    @Column(name = "assigned_by_user_id")
    private Long assignedByUserId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Checks if the role assignment has expired.
     *
     * @return true if expiresAt is set and has passed; false otherwise
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    /**
     * Checks if the role assignment is currently active and not expired.
     *
     * @return true if the assignment is active and not expired; false otherwise
     */
    public boolean isActiveAssignment() {
        return active && !isExpired();
    }

    /**
     * Expires the assignment by setting expiresAt to the current timestamp and deactivating it.
     */
    public void expire() {
        this.expiresAt = Instant.now();
        this.active = false;
    }

    /**
     * Activates the assignment and clears any set expiration timestamp.
     */
    public void activate() {
        this.active = true;
        this.expiresAt = null;
    }

    /**
     * Deactivates the assignment.
     */
    public void deactivate() {
        this.active = false;
    }
}
