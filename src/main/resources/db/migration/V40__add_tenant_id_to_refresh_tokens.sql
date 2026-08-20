-- ==========================================================================
-- V40__add_tenant_id_to_refresh_tokens.sql
-- ==========================================================================
-- Add tenant_id column to refresh_tokens to explicitly bind sessions to a tenant context.
-- ==========================================================================

-- 1. Add tenant_id column referencing tenants(id)
ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

-- 2. Add foreign key constraint to tenants(id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_refresh_tokens_tenant'
    ) THEN
        ALTER TABLE refresh_tokens
            ADD CONSTRAINT fk_refresh_tokens_tenant
            FOREIGN KEY (tenant_id) REFERENCES tenants(id);
    END IF;
END $$;

-- 3. Backfill tenant_id from users.tenant_id for existing refresh token rows
UPDATE refresh_tokens rt
SET tenant_id = u.tenant_id
FROM users u
WHERE rt.user_id = u.id
  AND rt.tenant_id IS NULL
  AND u.tenant_id IS NOT NULL;

-- 4. Create indexes on tenant_id for efficient tenant-scoped lookups/revocations
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_tenant_id
    ON refresh_tokens (tenant_id);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_tenant
    ON refresh_tokens (user_id, tenant_id);
