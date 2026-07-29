package com.forumx.platform.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    @NotBlank(message = "Tenant slug is required")
    @Size(min = 2, max = 50, message = "Tenant slug must be between 2 and 50 characters")
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug must contain only lowercase alphanumeric characters and hyphens")
    private String slug;

    @Size(max = 50)
    private String subscriptionPlan;

    private Integer maxUsers;
    private Long storageQuotaMB;
    private String timezone;
    private String locale;
}
