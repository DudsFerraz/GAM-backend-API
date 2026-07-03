ALTER TYPE event_status_enum ADD VALUE IF NOT EXISTS 'LOCKED';
ALTER TYPE event_status_enum ADD VALUE IF NOT EXISTS 'FINALIZED';

ALTER TABLE events
    ADD COLUMN IF NOT EXISTS cancellation_reason TEXT;

ALTER TABLE roles
    ADD COLUMN IF NOT EXISTS system_managed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE permissions
    ADD COLUMN IF NOT EXISTS system_managed BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE roles
SET system_managed = TRUE
WHERE name IN ('SUDO', 'COORD', 'MEMBER', 'VISITOR');

UPDATE permissions
SET system_managed = TRUE
WHERE name IN (
    'MEMBER_GET',
    'MEMBER_SEARCH',
    'MEMBER_ACTIVATION',
    'MEMBER_GET_NON_ACTIVE',
    'MEMBER_MANAGE',
    'ACCOUNT_GET',
    'ACCOUNT_SEARCH',
    'EVENT_CREATE',
    'EVENT_SEARCH',
    'EVENT_GET_PRESENCES',
    'EVENT_GET_S',
    'EVENT_MANAGE',
    'PRESENCES_SEARCH',
    'event:create',
    'event:update',
    'event:delete',
    'event:view',
    'event:subscribe',
    'member:manage_status',
    'location:manage'
);

ALTER TABLE oratorios
    DROP CONSTRAINT IF EXISTS oratorios_event_id_key;

ALTER TABLE missas
    DROP CONSTRAINT IF EXISTS missas_event_id_key;

CREATE UNIQUE INDEX IF NOT EXISTS idx_oratorios_event_id_not_deleted
    ON oratorios (event_id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_missas_event_id_not_deleted
    ON missas (event_id)
    WHERE deleted_at IS NULL;

CREATE TABLE activity_logs (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL,
    actor_account_id UUID,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(100) NOT NULL,
    target_id UUID NOT NULL,
    reason TEXT,
    summary TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    request_id VARCHAR(100),
    ip_address VARCHAR(100),
    user_agent TEXT,

    CONSTRAINT fk_activity_logs_actor_account
        FOREIGN KEY(actor_account_id) REFERENCES accounts(id)
);

CREATE INDEX idx_activity_logs_occurred_at
    ON activity_logs (occurred_at DESC);

CREATE INDEX idx_activity_logs_target
    ON activity_logs (target_type, target_id);

CREATE INDEX idx_activity_logs_actor_account
    ON activity_logs (actor_account_id);
