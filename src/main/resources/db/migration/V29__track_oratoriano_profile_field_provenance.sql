ALTER TABLE oratorianos
    ADD COLUMN name_source_form_id UUID,
    ADD COLUMN name_source_signed_on DATE,
    ADD COLUMN name_manual_updated_at TIMESTAMPTZ,
    ADD COLUMN birth_date_source_form_id UUID,
    ADD COLUMN birth_date_source_signed_on DATE,
    ADD COLUMN birth_date_manual_updated_at TIMESTAMPTZ,
    ADD COLUMN phone_source_form_id UUID,
    ADD COLUMN phone_source_signed_on DATE,
    ADD COLUMN phone_manual_updated_at TIMESTAMPTZ;

