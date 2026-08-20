package com.forumx.platform.tenant.service;

import com.forumx.platform.tenant.dto.CreateTenantRequest;
import com.forumx.platform.tenant.dto.TenantResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlatformTenantService {
    TenantResponse createTenant(CreateTenantRequest request);
    Page<TenantResponse> listTenants(Pageable pageable);
    TenantResponse getTenant(Long tenantId);

    /**
     * Safely deactivates a tenant by marking it INACTIVE, applying soft-delete,
     * revoking all active user sessions, and revoking all outstanding invitations.
     * The tenant's data is preserved for auditing; no referential integrity is broken.
     *
     * @param tenantId the ID of the tenant to deactivate
     */
    void deactivateTenant(Long tenantId);
}
