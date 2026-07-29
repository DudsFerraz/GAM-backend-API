CREATE TABLE gam_locations (
    id UUID CONSTRAINT locations_id_not_null NOT NULL,
    name VARCHAR(255) CONSTRAINT locations_name_not_null NOT NULL,
    street VARCHAR(255),
    city VARCHAR(100) CONSTRAINT locations_city_not_null NOT NULL,
    state VARCHAR(50) CONSTRAINT locations_state_not_null NOT NULL,
    postal_code VARCHAR(20),
    country_code VARCHAR(2) CONSTRAINT locations_country_code_not_null NOT NULL,
    latitude NUMERIC(10, 8),
    longitude NUMERIC(11, 8),

    created_at TIMESTAMPTZ CONSTRAINT locations_created_at_not_null NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ CONSTRAINT locations_updated_at_not_null NOT NULL,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,

    identity_name TEXT NOT NULL,
    identity_street TEXT NOT NULL,
    identity_city TEXT NOT NULL,
    identity_state TEXT NOT NULL,
    identity_postal_code TEXT NOT NULL,
    identity_country_code TEXT NOT NULL,
    code VARCHAR(32),
    system_managed BOOLEAN NOT NULL DEFAULT FALSE,
    catalog_current BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT locations_pkey PRIMARY KEY (id),
    CONSTRAINT fk_location_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT fk_location_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT fk_location_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT check_gam_locations_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL),
    CONSTRAINT check_gam_locations_system_ownership
        CHECK (system_managed = (code IS NOT NULL)),
    CONSTRAINT check_gam_locations_catalog_current
        CHECK (NOT catalog_current OR system_managed),
    CONSTRAINT check_gam_locations_code_format
        CHECK (code IS NULL OR code ~ '^[A-Z][A-Z0-9_]*$')
);

CREATE UNIQUE INDEX idx_gam_location_active_duplicate_identity
    ON gam_locations (
        identity_name,
        identity_street,
        identity_city,
        identity_state,
        identity_postal_code,
        identity_country_code
    )
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX idx_gam_locations_code
    ON gam_locations (code);
