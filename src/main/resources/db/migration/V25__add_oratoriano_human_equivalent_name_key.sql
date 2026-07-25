ALTER TABLE oratorianos
    ADD COLUMN name_key TEXT;

UPDATE oratorianos
SET name_key = regexp_replace(
        normalize(lower(first_name || ' ' || surname), NFD),
        U&'[\0300-\036F]',
        '',
        'g'
    );

ALTER TABLE oratorianos
    ALTER COLUMN first_name TYPE VARCHAR(32),
    ALTER COLUMN surname TYPE VARCHAR(64),
    ALTER COLUMN name_key SET NOT NULL;

CREATE UNIQUE INDEX idx_oratorianos_reserved_name_key
    ON oratorianos (name_key);
