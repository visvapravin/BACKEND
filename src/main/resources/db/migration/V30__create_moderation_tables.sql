-- Migration V30: Decoupled Enterprise Moderation Tables

CREATE TABLE IF NOT EXISTS moderation_reports (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT REFERENCES tenants(id),
    reporter_id       BIGINT REFERENCES users(id),
    target_type       VARCHAR(50) NOT NULL,
    target_id         BIGINT NOT NULL,
    reason            VARCHAR(50) NOT NULL,
    description       TEXT,
    status            VARCHAR(50) NOT NULL,
    priority          VARCHAR(50) NOT NULL,
    report_count      INT NOT NULL DEFAULT 1,
    assigned_to_id    BIGINT REFERENCES users(id),
    assigned_at        TIMESTAMP WITH TIME ZONE,
    review_started_at  TIMESTAMP WITH TIME ZONE,
    resolved_at        TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    version           BIGINT NOT NULL DEFAULT 0,
    deleted           BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS moderation_histories (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT REFERENCES tenants(id),
    report_id         BIGINT REFERENCES moderation_reports(id) ON DELETE CASCADE,
    moderator_id      BIGINT REFERENCES users(id),
    action            VARCHAR(50) NOT NULL,
    previous_status   VARCHAR(50) NOT NULL,
    new_status        VARCHAR(50) NOT NULL,
    decision_notes    TEXT,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    version           BIGINT NOT NULL DEFAULT 0,
    deleted           BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_mod_reports_tenant ON moderation_reports(tenant_id);
CREATE INDEX IF NOT EXISTS idx_mod_reports_target ON moderation_reports(target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_mod_reports_status ON moderation_reports(status);
CREATE INDEX IF NOT EXISTS idx_mod_reports_priority ON moderation_reports(priority);

CREATE INDEX IF NOT EXISTS idx_mod_history_report ON moderation_histories(report_id);
