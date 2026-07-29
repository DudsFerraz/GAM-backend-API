CREATE TABLE activity_logs (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL,
    actor_account_id UUID,
    actor_kind VARCHAR(20) NOT NULL,
    actor_reference VARCHAR(255),
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(100) NOT NULL,
    target_id UUID,
    target_scope VARCHAR(255),
    reason TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    request_id UUID,

    CONSTRAINT check_activity_logs_actor_form CHECK (
        (actor_kind = 'ACCOUNT' AND actor_account_id IS NOT NULL AND actor_reference IS NULL)
        OR (actor_kind = 'ANONYMOUS' AND actor_account_id IS NULL AND actor_reference IS NULL)
        OR (actor_kind IN ('SYSTEM', 'DEVELOPER')
            AND actor_account_id IS NULL
            AND actor_reference IS NOT NULL)
    ),
    CONSTRAINT check_activity_logs_target_form CHECK (
        (target_id IS NOT NULL AND target_scope IS NULL)
        OR (target_id IS NULL AND target_scope IS NOT NULL)
    )
);

CREATE INDEX idx_activity_logs_occurred_at
    ON activity_logs (occurred_at DESC);

CREATE INDEX idx_activity_logs_target
    ON activity_logs (target_type, target_id);

CREATE INDEX idx_activity_logs_actor_account
    ON activity_logs (actor_account_id);

CREATE INDEX idx_activity_logs_request_id
    ON activity_logs (request_id)
    WHERE request_id IS NOT NULL;
