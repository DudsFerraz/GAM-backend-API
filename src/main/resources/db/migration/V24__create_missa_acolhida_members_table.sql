CREATE TABLE missa_acolhida_members (
    member_id UUID,
    missa_id UUID,

    CONSTRAINT fk_acolhida_member
        FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT fk_acolhida_missa
        FOREIGN KEY (missa_id) REFERENCES missas(id)
);
