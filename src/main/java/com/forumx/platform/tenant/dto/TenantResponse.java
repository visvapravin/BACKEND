package com.forumx.platform.tenant.dto;

import java.time.Instant;
import com.forumx.tenant.entity.Tenant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResponse {
    private Long id;
    private String name;
    private String slug;
    private Tenant.TenantStatus status;
    private Tenant.SubscriptionPlan subscriptionPlan;
    private Integer maxUsers;
    private Long storageQuotaMB;
    private String timezone;
    private String locale;
    private String accessUrl;
    private Instant createdAt;
}
