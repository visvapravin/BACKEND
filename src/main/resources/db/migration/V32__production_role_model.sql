-- ==========================================================================
-- V32__production_role_model.sql
-- ==========================================================================
-- Migration to transition core authentication role names to production model:
--   SUPER_ADMIN  -> PLATFORM_ADMIN
--   ADMIN        -> TENANT_ADMIN
--
-- Preserves existing role_id references in user_roles and role_permissions.
-- ==========================================================================

-- 1. Rename existing ADMIN to TENANT_ADMIN if present
UPDATE roles
SET role_name = 'TENANT_ADMIN',
    description = 'Tenant Administrator Role',
    updated_at = CURRENT_TIMESTAMP
WHERE role_name = 'ADMIN';

-- 2. Rename existing SUPER_ADMIN to PLATFORM_ADMIN if present
UPDATE roles
SET role_name = 'PLATFORM_ADMIN',
    description = 'Platform Administrator Role',
    updated_at = CURRENT_TIMESTAMP
WHERE role_name = 'SUPER_ADMIN';

-- 3. Ensure TENANT_ADMIN role exists
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
    'TENANT_ADMIN',
    'Tenant Administrator Role',
    true
) ON CONFLICT (role_name) DO NOTHING;

-- 4. Ensure PLATFORM_ADMIN role exists
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
    'PLATFORM_ADMIN',
    'Platform Administrator Role',
    true
) ON CONFLICT (role_name) DO NOTHING;
