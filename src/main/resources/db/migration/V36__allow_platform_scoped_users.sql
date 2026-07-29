-- Platform identities deliberately have no tenant.  Tenant identities remain
-- constrained by application creation paths and role-aware authentication.
ALTER TABLE users ALTER COLUMN tenant_id DROP NOT NULL;

-- PostgreSQL permits multiple NULLs in the existing tenant/user-name unique
-- constraint; make platform usernames unambiguous for platform login.
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_platform_username
    ON users (username) WHERE tenant_id IS NULL AND deleted = false;
