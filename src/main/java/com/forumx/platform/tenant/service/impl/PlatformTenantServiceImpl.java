package com.forumx.platform.tenant.service.impl;

import com.forumx.platform.tenant.dto.CreateTenantRequest;
import com.forumx.platform.tenant.dto.TenantResponse;
import com.forumx.platform.tenant.service.PlatformTenantService;
import com.forumx.tenant.entity.Tenant;
import com.forumx.tenant.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformTenantServiceImpl implements PlatformTenantService {

    private final TenantRepository tenantRepository;

    @Override
    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        String normalizedSlug = request.getSlug().trim().toLowerCase();
        if (tenantRepository.existsBySlug(normalizedSlug)) {
            throw new IllegalArgumentException("Tenant with slug '" + normalizedSlug + "' already exists");
        }

        Tenant.SubscriptionPlan plan = Tenant.SubscriptionPlan.FREE;
        if (request.getSubscriptionPlan() != null && !request.getSubscriptionPlan().isBlank()) {
            try {
                plan = Tenant.SubscriptionPlan.valueOf(request.getSubscriptionPlan().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown subscription plan '{}', falling back to FREE", request.getSubscriptionPlan());
            }
        }

        Tenant tenant = Tenant.builder()
                .name(request.getName().trim())
                .slug(normalizedSlug)
                .status(Tenant.TenantStatus.ACTIVE)
                .subscriptionPlan(plan)
                .maxUsers(request.getMaxUsers() != null ? request.getMaxUsers() : 100)
                .storageQuotaMB(request.getStorageQuotaMB() != null ? request.getStorageQuotaMB() : 1024L)
                .timezone(request.getTimezone() != null ? request.getTimezone() : "UTC")
                .locale(request.getLocale() != null ? request.getLocale() : "en_US")
                .build();

        Tenant saved = tenantRepository.save(tenant);
        log.info("PLATFORM_TENANT_CREATED tenantId={} slug={}", saved.getId(), saved.getSlug());
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TenantResponse> listTenants(Pageable pageable) {
        return tenantRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TenantResponse getTenant(Long tenantId) {
        Tenant tenant = tenantRepository.findByIdAndDeletedFalse(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found with ID: " + tenantId));
        return mapToResponse(tenant);
    }

    private TenantResponse mapToResponse(Tenant tenant) {
        return TenantResponse.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .slug(tenant.getSlug())
                .status(tenant.getStatus())
                .subscriptionPlan(tenant.getSubscriptionPlan())
                .maxUsers(tenant.getMaxUsers())
                .storageQuotaMB(tenant.getStorageQuotaMB())
                .timezone(tenant.getTimezone())
                .locale(tenant.getLocale())
                .createdAt(tenant.getCreatedAt())
                .build();
    }
}
