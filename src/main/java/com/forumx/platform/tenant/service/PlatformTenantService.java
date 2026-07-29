package com.forumx.platform.tenant.service;

import com.forumx.platform.tenant.dto.CreateTenantRequest;
import com.forumx.platform.tenant.dto.TenantResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlatformTenantService {
    TenantResponse createTenant(CreateTenantRequest request);
    Page<TenantResponse> listTenants(Pageable pageable);
    TenantResponse getTenant(Long tenantId);
}
