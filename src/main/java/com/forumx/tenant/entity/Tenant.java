package com.forumx.tenant.entity;

import java.util.HashSet;
import java.util.Set;

import com.forumx.common.entity.BaseEntity;
import com.forumx.auth.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
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
        name = "tenants",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tenants_name", columnNames = "name"),
                @UniqueConstraint(name = "uk_tenants_slug", columnNames = "slug")
        },
        indexes = {
                @Index(name = "idx_tenants_status", columnList = "status"),
                @Index(name = "idx_tenants_subscription_plan", columnList = "subscription_plan")
        }
)
public class Tenant extends BaseEntity {

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100, unique = true)
    private String name;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100, unique = true)
    private String slug;

    @Size(max = 500)
    @Column(length = 500)
    private String description;

    @Size(max = 500)
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Size(max = 255)
    @Column(length = 255)
    private String website;

    @Email
    @Size(max = 255)
    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Size(max = 30)
    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TenantStatus status = TenantStatus.ACTIVE;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_plan", nullable = false, length = 20)
    private SubscriptionPlan subscriptionPlan = SubscriptionPlan.FREE;

    @Builder.Default
    @Column(name = "max_users", nullable = false)
    private Integer maxUsers = 50;

    @Builder.Default
    @Column(name = "storage_quota_mb", nullable = false)
    private Long storageQuotaMB = 1024L;

    @Size(max = 50)
    @Column(length = 50)
    private String timezone;

    @Size(max = 10)
    @Column(length = 10)
    private String locale;

    @Builder.Default
    @OneToMany(mappedBy = "tenant", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private Set<User> users = new HashSet<>();

    // ── Domain methods ──────────────────────────────────────────────────

    public void activate() {
        this.status = TenantStatus.ACTIVE;
    }

    public void suspend() {
        this.status = TenantStatus.SUSPENDED;
    }

    public void deactivate() {
        this.status = TenantStatus.INACTIVE;
    }

    public boolean isActive() {
        return this.status == TenantStatus.ACTIVE;
    }

    // ── Enums ───────────────────────────────────────────────────────────

    public enum TenantStatus {
        ACTIVE,
        INACTIVE,
        SUSPENDED,
        PENDING
    }

    public enum SubscriptionPlan {
        FREE,
        STARTER,
        PROFESSIONAL,
        ENTERPRISE
    }
}
