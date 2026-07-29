CREATE TYPE member_status_enum AS ENUM ('ACTIVE', 'INACTIVE');

CREATE TABLE members (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    surname VARCHAR(255) NOT NULL,
    birth_date DATE NOT NULL,
    phone_number VARCHAR(30) NOT NULL,
    status member_status_enum NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,

    CONSTRAINT fk_member_account
        FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_member_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT fk_member_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT fk_member_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT check_members_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL)
);

CREATE UNIQUE INDEX idx_members_account_id
    ON members (account_id);
