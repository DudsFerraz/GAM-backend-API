CREATE TABLE missa_assignments (
    missa_id UUID NOT NULL,
    responsibility VARCHAR(32) NOT NULL,
    member_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,

    CONSTRAINT pk_missa_assignments
        PRIMARY KEY (missa_id, responsibility, member_id),
    CONSTRAINT fk_missa_assignments_missa
        FOREIGN KEY (missa_id) REFERENCES missas(id) ON DELETE CASCADE,
    CONSTRAINT fk_missa_assignments_member
        FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE RESTRICT,
    CONSTRAINT fk_missa_assignments_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT check_missa_assignments_responsibility
        CHECK (responsibility IN (
            'COMENTARIOS',
            'PRIMEIRA_LEITURA',
            'SALMO',
            'SEGUNDA_LEITURA',
            'PRECES',
            'ACOLHIDA',
            'BANDA'
        ))
);

CREATE UNIQUE INDEX idx_missa_assignments_single_member
    ON missa_assignments (missa_id, responsibility)
    WHERE responsibility IN (
        'COMENTARIOS',
        'PRIMEIRA_LEITURA',
        'SALMO',
        'SEGUNDA_LEITURA',
        'PRECES'
    );

INSERT INTO missa_assignments (missa_id, responsibility, member_id, created_at, created_by)
SELECT missa.id, legacy.responsibility, legacy.member_id, missa.created_at, missa.created_by
FROM missas missa
CROSS JOIN LATERAL (VALUES
    ('COMENTARIOS', missa.comentarios_member),
    ('PRIMEIRA_LEITURA', missa.leitura_1_member),
    ('SALMO', missa.salmo_member),
    ('SEGUNDA_LEITURA', missa.leitura_2_member),
    ('PRECES', missa.preces_member)
) AS legacy(responsibility, member_id)
WHERE legacy.member_id IS NOT NULL;

INSERT INTO missa_assignments (missa_id, responsibility, member_id, created_at, created_by)
SELECT acolhida.missa_id, 'ACOLHIDA', acolhida.member_id, missa.created_at, missa.created_by
FROM missa_acolhida_members acolhida
JOIN missas missa ON missa.id = acolhida.missa_id
WHERE acolhida.missa_id IS NOT NULL
  AND acolhida.member_id IS NOT NULL
ON CONFLICT DO NOTHING;

DROP TABLE missa_acolhida_members;

ALTER TABLE missas
    DROP COLUMN comentarios_member,
    DROP COLUMN leitura_1_member,
    DROP COLUMN salmo_member,
    DROP COLUMN leitura_2_member,
    DROP COLUMN preces_member;

ALTER TABLE missas
    ADD CONSTRAINT check_missas_shared_identity CHECK (id = event_id);
