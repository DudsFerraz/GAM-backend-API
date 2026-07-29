CREATE TABLE missas (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    comentarios_member UUID,
    leitura_1_member UUID,
    salmo_member UUID,
    leitura_2_member UUID,
    preces_member UUID,

    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,

    CONSTRAINT fk_missa_event
        FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT fk_comentarios_member
        FOREIGN KEY (comentarios_member) REFERENCES members(id),
    CONSTRAINT fk_leitura_1_member
        FOREIGN KEY (leitura_1_member) REFERENCES members(id),
    CONSTRAINT fk_salmo_member
        FOREIGN KEY (salmo_member) REFERENCES members(id),
    CONSTRAINT fk_leitura_2_member
        FOREIGN KEY (leitura_2_member) REFERENCES members(id),
    CONSTRAINT fk_preces_member
        FOREIGN KEY (preces_member) REFERENCES members(id),
    CONSTRAINT fk_missa_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT fk_missa_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT fk_missa_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT check_missas_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL)
);

CREATE UNIQUE INDEX idx_missas_event_id_not_deleted
    ON missas (event_id)
    WHERE deleted_at IS NULL;
