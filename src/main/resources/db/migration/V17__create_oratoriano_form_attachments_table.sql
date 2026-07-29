CREATE TABLE oratoriano_form_attachments (
    id UUID PRIMARY KEY,
    form_id UUID NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    verified_mime_type VARCHAR(100) NOT NULL,
    byte_length BIGINT NOT NULL,
    page_order INTEGER NOT NULL,
    sha256 CHAR(64) NOT NULL,
    bytes BYTEA NOT NULL,
    page_count INTEGER NOT NULL DEFAULT 1,

    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,

    CONSTRAINT fk_oratoriano_form_attachment_form
        FOREIGN KEY (form_id) REFERENCES oratoriano_additional_forms(id) ON DELETE RESTRICT,
    CONSTRAINT fk_oratoriano_form_attachment_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT fk_oratoriano_form_attachment_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT fk_oratoriano_form_attachment_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT check_oratoriano_form_attachment_page_count
        CHECK (page_count > 0),
    CONSTRAINT check_oratoriano_form_attachments_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL)
);

CREATE UNIQUE INDEX idx_oratoriano_form_attachment_active_page
    ON oratoriano_form_attachments (form_id, page_order)
    WHERE deleted_at IS NULL;
