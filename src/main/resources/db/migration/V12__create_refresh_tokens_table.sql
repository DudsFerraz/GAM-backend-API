CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    token UUID NOT NULL UNIQUE,
    expiry_date TIMESTAMPTZ NOT NULL,
    account_id UUID NOT NULL,

    CONSTRAINT fk_refresh_token_account
        FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);
