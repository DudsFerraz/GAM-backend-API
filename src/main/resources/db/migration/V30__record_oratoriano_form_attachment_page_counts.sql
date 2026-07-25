ALTER TABLE oratoriano_form_attachments
    ADD COLUMN page_count INTEGER NOT NULL DEFAULT 1;

ALTER TABLE oratoriano_form_attachments
    ADD CONSTRAINT check_oratoriano_form_attachment_page_count
        CHECK (page_count > 0);

