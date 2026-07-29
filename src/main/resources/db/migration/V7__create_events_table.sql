CREATE TYPE event_status_enum AS ENUM (
    'SCHEDULED',
    'COMPLETED',
    'LOCKED',
    'FINALIZED',
    'CANCELLED'
);

CREATE TYPE event_type_enum AS ENUM ('GENERIC', 'ORATORIO', 'MISSA');

CREATE TABLE events (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    gam_location_id UUID NOT NULL,
    required_permission_id UUID,
    type event_type_enum NOT NULL,
    status event_status_enum NOT NULL,
    cancellation_reason TEXT,

    begin_date TIMESTAMPTZ NOT NULL,
    end_date TIMESTAMPTZ NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,

    CONSTRAINT check_event_dates CHECK (end_date > begin_date),
    CONSTRAINT fk_event_gam_location
        FOREIGN KEY (gam_location_id) REFERENCES gam_locations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_event_permission
        FOREIGN KEY (required_permission_id) REFERENCES permissions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_event_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT fk_event_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT fk_event_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT check_events_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL)
);
