package com.forumx.platform.tenant.controller;

import com.forumx.common.dto.ApiResponse;
import com.forumx.platform.tenant.dto.CreateTenantRequest;
import com.forumx.platform.tenant.dto.TenantResponse;
import com.forumx.platform.tenant.service.PlatformTenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/platform/tenants")
@Tag(name = "Platform Tenant Management", description = "Platform Admin operations for provisioning and managing tenants")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformTenantController {

    private final PlatformTenantService platformTenantService;

    @PostMapping
    @Operation(summary = "Create Tenant", description = "Creates a new tenant. Restricted to PLATFORM_ADMIN.")
    public ResponseEntity<ApiResponse<TenantResponse>> createTenant(
            @Valid @RequestBody CreateTenantRequest request
    ) {
        TenantResponse response = platformTenantService.createTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tenant created successfully", response));
    }

    @GetMapping
    @Operation(summary = "List Tenants", description = "Returns a paginated list of all tenants. Restricted to PLATFORM_ADMIN.")
    public ResponseEntity<ApiResponse<Page<TenantResponse>>> listTenants(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        Page<TenantResponse> page = platformTenantService.listTenants(pageable);
        return ResponseEntity.ok(ApiResponse.success("Tenants retrieved successfully", page));
    }

    @GetMapping("/{tenantId}")
    @Operation(summary = "Get Tenant Details", description = "Returns detail for a single tenant by ID. Restricted to PLATFORM_ADMIN.")
    public ResponseEntity<ApiResponse<TenantResponse>> getTenant(
            @PathVariable Long tenantId
    ) {
        TenantResponse response = platformTenantService.getTenant(tenantId);
        return ResponseEntity.ok(ApiResponse.success("Tenant retrieved successfully", response));
    }

    @DeleteMapping("/{tenantId}")
    @Operation(
            summary = "Deactivate Tenant",
            description = """
                    Safely deactivates a tenant workspace. The tenant is marked INACTIVE and soft-deleted.
                    All active user sessions (refresh tokens) are revoked immediately.
                    All outstanding PENDING moderator and tenant-admin invitations are revoked.
                    Tenant data is preserved for auditing. Restricted to PLATFORM_ADMIN.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Tenant deactivated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient role — PLATFORM_ADMIN required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Tenant not found or already deactivated/deleted"),
    })
    public ResponseEntity<Void> deactivateTenant(
            @PathVariable Long tenantId
    ) {
        platformTenantService.deactivateTenant(tenantId);
        return ResponseEntity.noContent().build();
    }
}
