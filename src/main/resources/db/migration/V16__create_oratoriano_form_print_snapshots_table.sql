CREATE TYPE oratoriano_form_print_mode_enum AS ENUM (
    'IDENTIFIED_BLANK',
    'PREFILLED'
);

CREATE TABLE oratoriano_form_print_snapshots (
    id UUID PRIMARY KEY,
    form_id UUID NOT NULL,
    draft_revision BIGINT NOT NULL,
    mode oratoriano_form_print_mode_enum NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    template_version VARCHAR(50) NOT NULL,
    page_count INTEGER NOT NULL,
    captured_data JSONB NOT NULL,
    fingerprint CHAR(64) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,

    CONSTRAINT fk_oratoriano_form_print_snapshot_form
        FOREIGN KEY (form_id) REFERENCES oratoriano_additional_forms(id) ON DELETE RESTRICT,
    CONSTRAINT fk_oratoriano_form_print_snapshot_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT fk_oratoriano_form_print_snapshot_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT fk_oratoriano_form_print_snapshot_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT check_oratoriano_form_print_snapshots_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL)
);
