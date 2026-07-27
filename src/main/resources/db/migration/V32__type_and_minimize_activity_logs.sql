ALTER TABLE activity_logs
    ADD COLUMN actor_kind VARCHAR(20),
    ADD COLUMN actor_reference VARCHAR(255),
    ADD COLUMN target_scope VARCHAR(255);

UPDATE activity_logs
SET actor_kind = CASE
    WHEN actor_account_id IS NULL THEN 'ANONYMOUS'
    ELSE 'ACCOUNT'
END;

ALTER TABLE activity_logs
    ALTER COLUMN actor_kind SET NOT NULL,
    ALTER COLUMN target_id DROP NOT NULL,
    ALTER COLUMN request_id TYPE UUID
        USING CASE
            WHEN request_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
                THEN request_id::UUID
            ELSE NULL
        END,
    DROP COLUMN summary,
    DROP COLUMN ip_address,
    DROP COLUMN user_agent;

ALTER TABLE activity_logs
    ADD CONSTRAINT check_activity_logs_actor_form CHECK (
        (actor_kind = 'ACCOUNT' AND actor_account_id IS NOT NULL AND actor_reference IS NULL)
        OR (actor_kind = 'ANONYMOUS' AND actor_account_id IS NULL AND actor_reference IS NULL)
        OR (actor_kind IN ('SYSTEM', 'DEVELOPER') AND actor_account_id IS NULL AND actor_reference IS NOT NULL)
    ),
    ADD CONSTRAINT check_activity_logs_target_form CHECK (
        (target_id IS NOT NULL AND target_scope IS NULL)
        OR (target_id IS NULL AND target_scope IS NOT NULL)
    );

CREATE INDEX idx_activity_logs_request_id
    ON activity_logs (request_id)
    WHERE request_id IS NOT NULL;
