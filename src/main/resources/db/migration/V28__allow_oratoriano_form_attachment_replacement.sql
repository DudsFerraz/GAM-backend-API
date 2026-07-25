ALTER TABLE oratoriano_form_attachments
    DROP CONSTRAINT idx_oratoriano_form_attachment_page;

CREATE UNIQUE INDEX idx_oratoriano_form_attachment_active_page
    ON oratoriano_form_attachments (form_id, page_order)
    WHERE deleted_at IS NULL;
