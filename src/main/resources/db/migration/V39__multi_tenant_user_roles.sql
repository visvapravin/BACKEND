-- ==========================================================================
-- V39__multi_tenant_user_roles.sql
-- ==========================================================================
-- Augment user_roles to support multi-tenant memberships:
-- A global user can hold different roles across multiple tenants.
-- ==========================================================================

-- 1. Add tenant_id column referencing tenants
ALTER TABLE user_roles
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

-- 2. Add foreign key constraint to tenants(id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_user_roles_tenant'
    ) THEN
        ALTER TABLE user_roles
            ADD CONSTRAINT fk_user_roles_tenant
            FOREIGN KEY (tenant_id) REFERENCES tenants(id);
    END IF;
END $$;

-- 3. Backfill tenant_id from users.tenant_id for existing non-platform users
UPDATE user_roles ur
SET tenant_id = u.tenant_id
FROM users u
WHERE ur.user_id = u.id
  AND ur.tenant_id IS NULL
  AND u.tenant_id IS NOT NULL;

-- 4. Drop the legacy (user_id, role_id) unique constraint
ALTER TABLE user_roles
    DROP CONSTRAINT IF EXISTS uk_user_roles_user_role;

-- 5. Add unique partial index for tenant-scoped role assignments
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_roles_user_tenant_role
    ON user_roles (user_id, tenant_id, role_id)
    WHERE tenant_id IS NOT NULL;

-- 6. Add unique partial index for platform-scoped role assignments (where tenant_id IS NULL)
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_roles_platform_role
    ON user_roles (user_id, role_id)
    WHERE tenant_id IS NULL;

-- 7. Add lookup indexes for user-tenant queries
CREATE INDEX IF NOT EXISTS idx_user_roles_tenant
    ON user_roles (tenant_id);

CREATE INDEX IF NOT EXISTS idx_user_roles_user_tenant
    ON user_roles (user_id, tenant_id);

CREATE INDEX IF NOT EXISTS idx_user_roles_user_tenant_active
    ON user_roles (user_id, tenant_id, active);
