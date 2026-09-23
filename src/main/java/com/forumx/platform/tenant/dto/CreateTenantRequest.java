package com.forumx.platform.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTenantRequest {

    @NotBlank(message = "Tenant name is required")
    @Size(max = 100, message = "Tenant name must not exceed 100 characters")
    private String name;

    @Size(max = 50, message = "Tenant slug must not exceed 50 characters")
    private String slug;

    @Size(max = 50)
    private String subscriptionPlan;

    private Integer maxUsers;
    private Long storageQuotaMB;
    private String timezone;
    private String locale;
}
