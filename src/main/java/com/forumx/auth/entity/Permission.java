package com.forumx.auth.entity;

import java.util.HashSet;
import java.util.Set;

import com.forumx.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
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
        name = "permissions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_permissions_permission_code", columnNames = "permission_code")
        },
        indexes = {
                @Index(name = "idx_permissions_permission_code", columnList = "permission_code"),
                @Index(name = "idx_permissions_module", columnList = "module"),
                @Index(name = "idx_permissions_active", columnList = "active")
        }
)
public class Permission extends BaseEntity {

    @NotBlank
    @Size(max = 100)
    @Column(name = "permission_code", nullable = false, unique = true, length = 100)
    private String permissionCode;

    @Size(max = 150)
    @Column(name = "display_name", length = 150)
    private String displayName;

    @Size(max = 500)
    @Column(length = 500)
    private String description;

    @Size(max = 100)
    @Column(length = 100)
    private String module;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @OneToMany(mappedBy = "permission", fetch = FetchType.LAZY)
    private Set<RolePermission> rolePermissions = new HashSet<>();

    // ── Domain methods ──────────────────────────────────────────────────

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
