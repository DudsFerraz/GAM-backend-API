CREATE TABLE oratorio_bt_jovens (
    member_id UUID NOT NULL,
    oratorio_id UUID NOT NULL,

    PRIMARY KEY (oratorio_id, member_id),
    CONSTRAINT fk_bt_jovens_member
        FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT fk_bt_jovens_oratorio
        FOREIGN KEY (oratorio_id) REFERENCES oratorios(id)
);
