CREATE TABLE oratorios (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    cancellation_reason TEXT,
    local_date DATE NOT NULL,
    lanche_description TEXT,
    gincana_description TEXT,
    boa_tarde_criancas_plan TEXT,
    boa_tarde_jovens_plan TEXT,

    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,

    CONSTRAINT fk_oratorio_event
        FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT fk_oratorio_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT fk_oratorio_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT fk_oratorio_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT check_oratorios_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL)
);

CREATE UNIQUE INDEX idx_oratorios_event_id_not_deleted
    ON oratorios (event_id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX idx_oratorios_active_local_date
    ON oratorios (local_date)
    WHERE deleted_at IS NULL;
