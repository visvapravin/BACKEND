-- ==========================================================================
-- V35__ensure_production_roles.sql
-- ==========================================================================
-- Idempotent guard: ensures all 5 production roles exist in the roles table.
-- Runs as INSERT ... ON CONFLICT DO NOTHING so it is safe to apply to any
-- database state (fresh, partially migrated, or fully migrated).
-- ==========================================================================

INSERT INTO roles (
    created_at, updated_at, created_by, updated_by,
    version, deleted, deleted_at, role_name, description, active
) VALUES
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM', 0, false, NULL,
     'PLATFORM_ADMIN', 'Platform Administrator Role', true),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM', 0, false, NULL,
     'TENANT_ADMIN', 'Tenant Administrator Role', true),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM', 0, false, NULL,
     'MODERATOR', 'Moderator Role', true),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM', 0, false, NULL,
     'USER', 'Default User Role', true),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM', 0, false, NULL,
     'GUEST', 'Guest Role', true)
ON CONFLICT (role_name) DO NOTHING;
