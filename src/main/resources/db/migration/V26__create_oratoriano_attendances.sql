CREATE TABLE oratoriano_attendances (
    id UUID PRIMARY KEY,
    oratorio_id UUID NOT NULL,
    oratoriano_id UUID NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,

    CONSTRAINT fk_oratoriano_attendance_oratorio
        FOREIGN KEY (oratorio_id) REFERENCES oratorios(id) ON DELETE RESTRICT,
    CONSTRAINT fk_oratoriano_attendance_oratoriano
        FOREIGN KEY (oratoriano_id) REFERENCES oratorianos(id) ON DELETE RESTRICT,
    CONSTRAINT fk_oratoriano_attendance_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id),
    CONSTRAINT fk_oratoriano_attendance_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id),
    CONSTRAINT fk_oratoriano_attendance_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id)
);

CREATE UNIQUE INDEX idx_oratoriano_attendance_active_pair
    ON oratoriano_attendances (oratorio_id, oratoriano_id)
    WHERE deleted_at IS NULL;
