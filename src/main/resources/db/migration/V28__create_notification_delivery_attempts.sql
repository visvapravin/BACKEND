-- Migration V28: Notification Delivery Attempts table for Reliable Delivery Framework
CREATE TABLE IF NOT EXISTS notification_delivery_attempts (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT REFERENCES tenants(id),
    notification_id   BIGINT REFERENCES notifications(id) ON DELETE SET NULL,
    channel           VARCHAR(30) NOT NULL,
    provider          VARCHAR(50),
    status            VARCHAR(30) NOT NULL,
    attempt_number    INT NOT NULL DEFAULT 1,
    correlation_id    VARCHAR(64) NOT NULL,
    provider_response TEXT,
    error_message     TEXT,
    started_at        TIMESTAMP WITH TIME ZONE,
    completed_at      TIMESTAMP WITH TIME ZONE,
    duration_ms       BIGINT,
    next_retry_at     TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    version           BIGINT NOT NULL DEFAULT 0,
    deleted           BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_nda_tenant ON notification_delivery_attempts(tenant_id);
CREATE INDEX IF NOT EXISTS idx_nda_notification ON notification_delivery_attempts(notification_id);
CREATE INDEX IF NOT EXISTS idx_nda_status ON notification_delivery_attempts(status);
CREATE INDEX IF NOT EXISTS idx_nda_channel ON notification_delivery_attempts(channel);
CREATE INDEX IF NOT EXISTS idx_nda_correlation ON notification_delivery_attempts(correlation_id);
CREATE INDEX IF NOT EXISTS idx_nda_attempt ON notification_delivery_attempts(attempt_number);
