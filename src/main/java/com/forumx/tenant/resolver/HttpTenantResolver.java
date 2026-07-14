package com.forumx.tenant.resolver;

import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * HTTP implementation of {@link TenantResolver} that resolves the active tenant slug
 * from headers, query parameters, or falls back to 'default'.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpTenantResolver implements TenantResolver {

    private final TenantRepository tenantRepository;

    /**
     * Resolves the active tenant ID from the HTTP request context or database fallback.
     *
     * @return the resolved tenant ID
     */
    @Override
    public Long resolveTenantId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return getDefaultTenantId();
        }

        HttpServletRequest request = attributes.getRequest();

        // 1. Try X-Tenant header
        String tenantSlug = request.getHeader("X-Tenant");
        if (tenantSlug == null) {
            tenantSlug = request.getHeader("X-Tenant-Slug");
        }

        // 2. Try query parameter
        if (tenantSlug == null) {
            tenantSlug = request.getParameter("tenant");
        }
        if (tenantSlug == null) {
            tenantSlug = request.getParameter("tenantSlug");
        }

        // 3. Fallback to default slug
        if (tenantSlug == null || tenantSlug.isBlank()) {
            tenantSlug = "default";
        }

        String finalSlug = tenantSlug;
        return tenantRepository.findBySlug(finalSlug)
                .map(Tenant::getId)
                .orElseGet(() -> {
                    log.warn("Tenant slug '{}' not found in database, falling back to default.", finalSlug);
                    return getDefaultTenantId();
                });
    }

    /**
     * Helper to retrieve the database ID for the 'default' tenant slug.
     *
     * @return the default tenant ID or null
     */
    private Long getDefaultTenantId() {
        return tenantRepository.findBySlug("default")
                .map(Tenant::getId)
                .orElse(null);
    }
}
