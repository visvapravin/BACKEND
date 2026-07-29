package com.forumx.auth.service;

import com.forumx.auth.enums.RoleType;
import com.forumx.tenant.entity.Tenant;
import org.springframework.stereotype.Component;

/** Trusted account-creation boundary: only platform administrators are tenantless. */
@Component
public class AccountScopeValidator {
    public void validate(RoleType role, Tenant tenant) {
        boolean platform = role == RoleType.PLATFORM_ADMIN;
        if (platform && tenant != null) throw new IllegalArgumentException("Platform accounts must not have a tenant");
        if (!platform && role != RoleType.GUEST && tenant == null)
            throw new IllegalArgumentException("Tenant-scoped accounts require a tenant");
    }
}
