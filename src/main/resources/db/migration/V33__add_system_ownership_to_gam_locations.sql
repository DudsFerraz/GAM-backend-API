ALTER TABLE gam_locations
    ADD COLUMN code VARCHAR(32),
    ADD COLUMN system_managed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN catalog_current BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE gam_locations
    ADD CONSTRAINT check_gam_locations_system_ownership
        CHECK (system_managed = (code IS NOT NULL)),
    ADD CONSTRAINT check_gam_locations_catalog_current
        CHECK (NOT catalog_current OR system_managed),
    ADD CONSTRAINT check_gam_locations_code_format
        CHECK (code IS NULL OR code ~ '^[A-Z][A-Z0-9_]*$');

CREATE UNIQUE INDEX idx_gam_locations_code
    ON gam_locations (code);
