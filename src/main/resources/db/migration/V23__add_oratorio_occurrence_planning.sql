ALTER TABLE oratorios
    ADD COLUMN local_date DATE,
    ADD COLUMN lanche_description TEXT,
    ADD COLUMN gincana_description TEXT,
    ADD COLUMN boa_tarde_criancas_plan TEXT,
    ADD COLUMN boa_tarde_jovens_plan TEXT;

UPDATE oratorios o
SET local_date = (e.begin_date AT TIME ZONE 'America/Sao_Paulo')::date
FROM events e
WHERE e.id = o.event_id;

ALTER TABLE oratorios
    ALTER COLUMN local_date SET NOT NULL;

CREATE UNIQUE INDEX idx_oratorios_active_local_date
    ON oratorios (local_date)
    WHERE deleted_at IS NULL;
