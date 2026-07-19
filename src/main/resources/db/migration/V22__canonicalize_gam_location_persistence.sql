ALTER TABLE locations RENAME TO gam_locations;

ALTER TABLE events RENAME COLUMN location_id TO gam_location_id;

ALTER TABLE events
    ALTER COLUMN gam_location_id SET NOT NULL;

ALTER TABLE gam_locations
    ALTER COLUMN country_code TYPE VARCHAR(2);

ALTER TABLE gam_locations
    ADD COLUMN identity_name TEXT,
    ADD COLUMN identity_street TEXT,
    ADD COLUMN identity_city TEXT,
    ADD COLUMN identity_state TEXT,
    ADD COLUMN identity_postal_code TEXT,
    ADD COLUMN identity_country_code TEXT;

UPDATE gam_locations
SET identity_name = regexp_replace(
        regexp_replace(normalize(lower(btrim(name)), NFD), U&'[\0300-\036F]', '', 'g'),
        '[[:space:]]+', ' ', 'g'),
    identity_street = regexp_replace(
        regexp_replace(normalize(lower(coalesce(btrim(street), '')), NFD), U&'[\0300-\036F]', '', 'g'),
        '[[:space:]]+', ' ', 'g'),
    identity_city = regexp_replace(
        regexp_replace(normalize(lower(btrim(city)), NFD), U&'[\0300-\036F]', '', 'g'),
        '[[:space:]]+', ' ', 'g'),
    identity_state = regexp_replace(
        regexp_replace(normalize(lower(btrim(state)), NFD), U&'[\0300-\036F]', '', 'g'),
        '[[:space:]]+', ' ', 'g'),
    identity_postal_code = regexp_replace(
        regexp_replace(normalize(lower(coalesce(btrim(postal_code), '')), NFD), U&'[\0300-\036F]', '', 'g'),
        '[[:space:]]+', ' ', 'g'),
    identity_country_code = regexp_replace(
        regexp_replace(normalize(lower(btrim(country_code)), NFD), U&'[\0300-\036F]', '', 'g'),
        '[[:space:]]+', ' ', 'g');

ALTER TABLE gam_locations
    ALTER COLUMN identity_name SET NOT NULL,
    ALTER COLUMN identity_street SET NOT NULL,
    ALTER COLUMN identity_city SET NOT NULL,
    ALTER COLUMN identity_state SET NOT NULL,
    ALTER COLUMN identity_postal_code SET NOT NULL,
    ALTER COLUMN identity_country_code SET NOT NULL;

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

ALTER TABLE events RENAME CONSTRAINT fk_event_location TO fk_event_gam_location;
