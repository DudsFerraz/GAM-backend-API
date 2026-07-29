CREATE TYPE membership_solicitation_status_enum AS ENUM (
    'PENDING',
    'APPROVED',
    'REJECTED'
);

CREATE TABLE membership_solicitations (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    surname VARCHAR(255) NOT NULL,
    birth_date DATE NOT NULL,
    phone_number VARCHAR(30) NOT NULL,
    justification VARCHAR(2000) NOT NULL,
    status membership_solicitation_status_enum NOT NULL,
    reviewed_by_account_id UUID,
    decided_at TIMESTAMPTZ,
    review_reason VARCHAR(2000),
    member_id UUID,
    version BIGINT NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,

    CONSTRAINT fk_membership_solicitations_account
        FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT fk_membership_solicitations_reviewed_by
        FOREIGN KEY (reviewed_by_account_id) REFERENCES accounts(id),
    CONSTRAINT fk_membership_solicitations_member
        FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT fk_membership_solicitations_created_by
        FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT fk_membership_solicitations_updated_by
        FOREIGN KEY (updated_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT fk_membership_solicitations_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT check_membership_solicitations_decision CHECK (
        (status = 'PENDING'
            AND reviewed_by_account_id IS NULL
            AND decided_at IS NULL
            AND review_reason IS NULL
            AND member_id IS NULL)
        OR
        (status = 'APPROVED'
            AND reviewed_by_account_id IS NOT NULL
            AND decided_at IS NOT NULL
            AND review_reason IS NOT NULL
            AND member_id IS NOT NULL)
        OR
        (status = 'REJECTED'
            AND reviewed_by_account_id IS NOT NULL
            AND decided_at IS NOT NULL
            AND review_reason IS NOT NULL
            AND member_id IS NULL)
    ),
    CONSTRAINT check_membership_solicitations_deleted_attribution
        CHECK (deleted_by IS NULL OR deleted_at IS NOT NULL)
);

CREATE UNIQUE INDEX idx_membership_solicitations_one_pending
    ON membership_solicitations (account_id)
    WHERE status = 'PENDING' AND deleted_at IS NULL;

CREATE UNIQUE INDEX idx_membership_solicitations_member
    ON membership_solicitations (member_id)
    WHERE member_id IS NOT NULL;
