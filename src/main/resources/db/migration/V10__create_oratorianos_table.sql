CREATE TABLE oratorianos (
    id UUID PRIMARY KEY,
    first_name VARCHAR(32) NOT NULL,
    surname VARCHAR(64) NOT NULL,
    birth_date DATE,
    phone_number VARCHAR(30),
    name_key TEXT NOT NULL,
    name_source_form_id UUID,
    name_source_signed_on DATE,
    name_manual_updated_at TIMESTAMPTZ,
    birth_date_source_form_id UUID,
    birth_date_source_signed_on DATE,
    birth_date_manual_updated_at TIMESTAMPTZ,
    phone_source_form_id UUID,
    phone_source_signed_on DATE,
    phone_manual_updated_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,

    CONSTRAINT fk_oratoriano_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT fk_oratoriano_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT fk_oratoriano_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT check_oratorianos_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL)
);

CREATE UNIQUE INDEX idx_oratorianos_reserved_name_key
    ON oratorianos (name_key);
