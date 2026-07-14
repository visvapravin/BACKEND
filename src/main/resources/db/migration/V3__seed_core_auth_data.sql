-- ==========================================================================
-- V3__seed_core_auth_data.sql
-- ==========================================================================
-- WHY THIS MIGRATION EXISTS:
-- In a production-ready application, core system-wide configurations and
-- master data must be version-controlled, automated, and deterministic.
-- Seeding core authentication master data using Flyway ensures that the
-- system is fully self-initializing upon deployment or fresh local setups.
--
-- MANDATORY RECORDS SEEDED:
-- 1. Default Tenant ('default'):
--    The authentication module expects users to register and login under a
--    valid tenant slug. Without a pre-existing tenant, the registration flow
--    fails with a "Tenant not found" error.
--
-- 2. Default Roles ('USER', 'ADMIN'):
--    When registering, users are automatically assigned the default 'USER' role.
--    AuthenticationService looks up these roles from the database using the
--    RoleType enum. If 'USER' is missing from the database, registration fails
--    with a "Default Role USER not found" error.
--
-- INTENTIONAL OMISSION OF PERMISSIONS:
-- The permissions and role_permissions tables are left empty.
-- The current application architecture extracts GrantedAuthorities and maps
-- them as roles (prefixed with 'ROLE_') and does not yet perform any fine-grained
-- permission checks. Seeding arbitrary permissions is avoided to prevent schema
-- bloat and maintain a clean separation of roles and permissions.
-- ==========================================================================

-- 1. Seed Default Tenant
INSERT INTO tenants (
    created_at,
    updated_at,
    created_by,
    updated_by,
    version,
    deleted,
    deleted_at,
    name,
    slug,
    description,
    logo_url,
    website,
    contact_email,
    contact_phone,
    status,
    subscription_plan,
    max_users,
    storage_quota_mb,
    timezone,
    locale
) VALUES (
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    'SYSTEM',
    0,
    false,
    NULL,
    'Default Tenant',
    'default',
    'Default workspace tenant',
    NULL,
    NULL,
    NULL,
    NULL,
    'ACTIVE',
    'FREE',
    100,
    1024,
    'UTC',
    'en_US'
) ON CONFLICT (slug) DO NOTHING;

-- 2. Seed Default Roles
INSERT INTO roles (
    created_at,
    updated_at,
    created_by,
    updated_by,
    version,
    deleted,
    deleted_at,
    role_name,
    description,
    active
) VALUES (
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    'SYSTEM',
    0,
    false,
    NULL,
    'USER',
    'Default User Role',
    true
) ON CONFLICT (role_name) DO NOTHING;

INSERT INTO roles (
    created_at,
    updated_at,
    created_by,
    updated_by,
    version,
    deleted,
    deleted_at,
    role_name,
    description,
    active
) VALUES (
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    'SYSTEM',
    0,
    false,
    NULL,
    'ADMIN',
    'Default Administrator Role',
    true
) ON CONFLICT (role_name) DO NOTHING;
