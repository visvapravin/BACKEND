package com.forumx.tenant.util;

import com.forumx.common.exception.InvalidTenantSlugException;
import com.forumx.common.exception.ReservedTenantSlugException;
import com.forumx.common.exception.TenantSlugAlreadyExistsException;
import com.forumx.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantSlugProvisionerTest {

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private TenantSlugProvisioner slugProvisioner;

    @BeforeEach
    void setUp() {
        // Default: no slugs exist unless stubbed otherwise in individual tests
        lenient().when(tenantRepository.existsBySlug(anyString())).thenReturn(false);
    }

    // ── Manual Slug Tests ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Manual slug: valid slug is accepted and preserved")
    void manualSlug_valid_accepted() {
        String result = slugProvisioner.validateAndNormalizeManualSlug("krct");
        assertThat(result).isEqualTo("krct");
    }

    @Test
    @DisplayName("Manual slug: uppercase is normalized to lowercase")
    void manualSlug_uppercase_normalizedToLowercase() {
        String result = slugProvisioner.validateAndNormalizeManualSlug("KRCT");
        assertThat(result).isEqualTo("krct");
    }

    @Test
    @DisplayName("Manual slug: trimmed and valid with hyphens")
    void manualSlug_withHyphensAndTrimmed_accepted() {
        String result = slugProvisioner.validateAndNormalizeManualSlug("  krct-community  ");
        assertThat(result).isEqualTo("krct-community");
    }

    @Test
    @DisplayName("Manual slug: spaces are rejected with InvalidTenantSlugException")
    void manualSlug_withSpaces_rejected() {
        assertThatThrownBy(() -> slugProvisioner.validateAndNormalizeManualSlug("krct community"))
                .isInstanceOf(InvalidTenantSlugException.class)
                .hasMessageContaining("only lowercase alphanumeric characters and hyphens");
    }

    @Test
    @DisplayName("Manual slug: leading/trailing hyphens are rejected")
    void manualSlug_leadingTrailingHyphens_rejected() {
        assertThatThrownBy(() -> slugProvisioner.validateAndNormalizeManualSlug("-krct-"))
                .isInstanceOf(InvalidTenantSlugException.class);
    }

    @Test
    @DisplayName("Manual slug: length < 2 is rejected")
    void manualSlug_tooShort_rejected() {
        assertThatThrownBy(() -> slugProvisioner.validateAndNormalizeManualSlug("a"))
                .isInstanceOf(InvalidTenantSlugException.class)
                .hasMessageContaining("between 2 and 50");
    }

    @Test
    @DisplayName("Manual slug: blank or null is rejected")
    void manualSlug_blankOrNull_rejected() {
        assertThatThrownBy(() -> slugProvisioner.validateAndNormalizeManualSlug("   "))
                .isInstanceOf(InvalidTenantSlugException.class);
        assertThatThrownBy(() -> slugProvisioner.validateAndNormalizeManualSlug(null))
                .isInstanceOf(InvalidTenantSlugException.class);
    }

    @Test
    @DisplayName("Manual slug: reserved slugs ('platform', 'admin', 'default', 'api') are rejected")
    void manualSlug_reservedSlugs_rejected() {
        assertThatThrownBy(() -> slugProvisioner.validateAndNormalizeManualSlug("platform"))
                .isInstanceOf(ReservedTenantSlugException.class)
                .hasMessageContaining("reserved");

        assertThatThrownBy(() -> slugProvisioner.validateAndNormalizeManualSlug("default"))
                .isInstanceOf(ReservedTenantSlugException.class)
                .hasMessageContaining("reserved");

        assertThatThrownBy(() -> slugProvisioner.validateAndNormalizeManualSlug("admin"))
                .isInstanceOf(ReservedTenantSlugException.class)
                .hasMessageContaining("reserved");

        assertThatThrownBy(() -> slugProvisioner.validateAndNormalizeManualSlug("api"))
                .isInstanceOf(ReservedTenantSlugException.class)
                .hasMessageContaining("reserved");
    }

    @Test
    @DisplayName("Manual slug: existing slug in repository is rejected with TenantSlugAlreadyExistsException")
    void manualSlug_alreadyExists_rejectedWithConflict() {
        when(tenantRepository.existsBySlug("krct")).thenReturn(true);

        assertThatThrownBy(() -> slugProvisioner.validateAndNormalizeManualSlug("krct"))
                .isInstanceOf(TenantSlugAlreadyExistsException.class)
                .hasMessageContaining("already exists");
    }

    // ── Auto-Generated Slug Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("Auto-generated slug: converts name with spaces and special characters")
    void autoGeneratedSlug_nameWithSpacesAndSpecialChars() {
        String slug1 = slugProvisioner.provisionAutoGeneratedSlug("KRCT Community", 0);
        assertThat(slug1).isEqualTo("krct-community");

        String slug2 = slugProvisioner.provisionAutoGeneratedSlug("ABC Company!", 0);
        assertThat(slug2).isEqualTo("abc-company");

        String slug3 = slugProvisioner.provisionAutoGeneratedSlug("  My   Organization  ", 0);
        assertThat(slug3).isEqualTo("my-organization");
    }

    @Test
    @DisplayName("Auto-generated slug: empty or symbols-only name falls back to safe base")
    void autoGeneratedSlug_emptyOrSymbols_fallback() {
        String slug = slugProvisioner.provisionAutoGeneratedSlug("$$$ @@@", 0);
        assertThat(slug).isEqualTo("tenant");
    }

    @Test
    @DisplayName("Auto-generated slug: collision resolution probes next numeric suffix")
    void autoGeneratedSlug_collisionResolution_probesNextSuffix() {
        // Simulate: "krct" exists, "krct-1" exists, "krct-2" is free
        when(tenantRepository.existsBySlug("krct")).thenReturn(true);
        when(tenantRepository.existsBySlug("krct-1")).thenReturn(true);
        when(tenantRepository.existsBySlug("krct-2")).thenReturn(false);

        String slug = slugProvisioner.provisionAutoGeneratedSlug("KRCT", 0);
        assertThat(slug).isEqualTo("krct-2");
    }

    @Test
    @DisplayName("Auto-generated slug: reserved base name ('Default', 'Admin') safely probes suffix")
    void autoGeneratedSlug_reservedBaseName_probesSafeSuffix() {
        // "default" is in RESERVED_SLUGS, so candidate 0 is skipped and "default-1" is probed
        String slugDefault = slugProvisioner.provisionAutoGeneratedSlug("Default", 0);
        assertThat(slugDefault).isEqualTo("default-1");

        // "admin" is in RESERVED_SLUGS, so candidate 0 is skipped and "admin-1" is probed
        String slugAdmin = slugProvisioner.provisionAutoGeneratedSlug("Admin", 0);
        assertThat(slugAdmin).isEqualTo("admin-1");
    }
}
