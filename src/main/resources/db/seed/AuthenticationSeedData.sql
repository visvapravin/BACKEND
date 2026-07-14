-- Idempotent SQL script to seed required Authentication data in ForumX Monorepo database

-- 1. Insert Default Tenant
INSERT INTO tenants (
    name, 
    slug, 
    status, 
    subscription_plan, 
    max_users, 
    storage_quota_mb, 
    version, 
    deleted, 
    created_at, 
    updated_at
)
VALUES (
    'Default Tenant', 
    'default', 
    'ACTIVE', 
    'FREE', 
    50, 
    1024, 
    1, 
    false, 
    CURRENT_TIMESTAMP, 
    CURRENT_TIMESTAMP
)
ON CONFLICT (slug) DO NOTHING;

-- 2. Insert Default USER Role
INSERT INTO roles (
    role_name, 
    description, 
    active, 
    version, 
    deleted, 
    created_at, 
    updated_at
)
VALUES (
    'USER', 
    'Default user role', 
    true, 
    1, 
    false, 
    CURRENT_TIMESTAMP, 
    CURRENT_TIMESTAMP
)
ON CONFLICT (role_name) DO NOTHING;

-- 3. Insert Default ADMIN Role
INSERT INTO roles (
    role_name, 
    description, 
    active, 
    version, 
    deleted, 
    created_at, 
    updated_at
)
VALUES (
    'ADMIN', 
    'Administrator role', 
    true, 
    1, 
    false, 
    CURRENT_TIMESTAMP, 
    CURRENT_TIMESTAMP
)
ON CONFLICT (role_name) DO NOTHING;
