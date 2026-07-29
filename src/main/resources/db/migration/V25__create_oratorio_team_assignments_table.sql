CREATE TYPE oratorio_team_type_enum AS ENUM (
    'LANCHE',
    'GINCANA',
    'BOA_TARDE_CRIANCAS',
    'BOA_TARDE_JOVENS'
);

CREATE TABLE oratorio_team_assignments (
    oratorio_id UUID NOT NULL,
    member_id UUID NOT NULL,
    team_type oratorio_team_type_enum NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,

    PRIMARY KEY (oratorio_id, member_id, team_type),
    CONSTRAINT fk_oratorio_team_assignment_oratorio
        FOREIGN KEY (oratorio_id) REFERENCES oratorios(id) ON DELETE RESTRICT,
    CONSTRAINT fk_oratorio_team_assignment_member
        FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE RESTRICT,
    CONSTRAINT fk_oratorio_team_assignment_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL
);
