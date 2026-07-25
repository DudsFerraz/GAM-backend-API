CREATE TYPE oratoriano_form_status_enum AS ENUM (
    'DRAFT',
    'COMPLETED',
    'SUPERSEDED',
    'REVOKED'
);

CREATE TYPE oratoriano_form_origin_enum AS ENUM (
    'PAPER_TRANSCRIPTION',
    'DIRECT_SYSTEM_ENTRY'
);

CREATE TYPE oratoriano_form_print_mode_enum AS ENUM (
    'IDENTIFIED_BLANK',
    'PREFILLED'
);

CREATE TABLE oratoriano_additional_forms (
    id UUID PRIMARY KEY,
    oratoriano_id UUID NOT NULL,
    version INTEGER NOT NULL,
    status oratoriano_form_status_enum NOT NULL,
    origin oratoriano_form_origin_enum NOT NULL,
    draft_revision BIGINT NOT NULL DEFAULT 1,
    draft_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    signed_on DATE,
    completed_at TIMESTAMPTZ,
    completed_by UUID,
    revoked_at TIMESTAMPTZ,
    revoked_by UUID,

    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,

    CONSTRAINT fk_oratoriano_additional_form_oratoriano
        FOREIGN KEY (oratoriano_id) REFERENCES oratorianos(id) ON DELETE RESTRICT,
    CONSTRAINT fk_oratoriano_additional_form_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id),
    CONSTRAINT fk_oratoriano_additional_form_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id),
    CONSTRAINT fk_oratoriano_additional_form_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id),
    CONSTRAINT fk_oratoriano_additional_form_completed_by
        FOREIGN KEY (completed_by) REFERENCES accounts(id),
    CONSTRAINT fk_oratoriano_additional_form_revoked_by
        FOREIGN KEY (revoked_by) REFERENCES accounts(id),
    CONSTRAINT idx_oratoriano_additional_form_version
        UNIQUE (oratoriano_id, version)
);

CREATE UNIQUE INDEX idx_oratoriano_additional_form_current
    ON oratoriano_additional_forms (oratoriano_id)
    WHERE status = 'COMPLETED' AND deleted_at IS NULL;

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
        FOREIGN KEY (created_by) REFERENCES accounts(id),
    CONSTRAINT fk_oratoriano_form_print_snapshot_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id),
    CONSTRAINT fk_oratoriano_form_print_snapshot_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id)
);

CREATE TABLE oratoriano_form_attachments (
    id UUID PRIMARY KEY,
    form_id UUID NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    verified_mime_type VARCHAR(100) NOT NULL,
    byte_length BIGINT NOT NULL,
    page_order INTEGER NOT NULL,
    sha256 CHAR(64) NOT NULL,
    bytes BYTEA NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,

    CONSTRAINT fk_oratoriano_form_attachment_form
        FOREIGN KEY (form_id) REFERENCES oratoriano_additional_forms(id) ON DELETE RESTRICT,
    CONSTRAINT fk_oratoriano_form_attachment_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id),
    CONSTRAINT fk_oratoriano_form_attachment_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id),
    CONSTRAINT fk_oratoriano_form_attachment_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id),
    CONSTRAINT idx_oratoriano_form_attachment_page
        UNIQUE (form_id, page_order)
);
