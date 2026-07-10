package com.forumx.auth.entity;

import java.util.HashSet;
import java.util.Set;

import com.forumx.auth.enums.RoleType;
import com.forumx.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
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
        name = "roles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_roles_role_name", columnNames = "role_name")
        },
        indexes = {
                @Index(name = "idx_roles_role_name", columnList = "role_name"),
                @Index(name = "idx_roles_active", columnList = "active")
        }
)
public class Role extends BaseEntity {

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "role_name", nullable = false, unique = true, length = 50)
    private RoleType roleName;

    @Size(max = 255)
    @Column(length = 255)
    private String description;

    @lombok.Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @lombok.Builder.Default
    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY)
    private Set<UserRole> userRoles = new HashSet<>();

    @lombok.Builder.Default
    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY)
    private Set<RolePermission> rolePermissions = new HashSet<>();

    // ── Domain methods ──────────────────────────────────────────────────

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
