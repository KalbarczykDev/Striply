CREATE TABLE refresh_token_family
(
    id                  UUID        NOT NULL,
    user_id             UUID        NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    revoked_at          TIMESTAMPTZ,
    revocation_reason   VARCHAR(32),

    CONSTRAINT pk_refresh_token_family
        PRIMARY KEY (id),

    CONSTRAINT fk_refresh_token_family_user
        FOREIGN KEY (user_id)
            REFERENCES app_user (id)
            ON DELETE RESTRICT,

    CONSTRAINT ck_refresh_token_family_expiry
        CHECK (absolute_expires_at > created_at),

    CONSTRAINT ck_refresh_token_family_revoked_at
        CHECK (revoked_at IS NULL OR revoked_at >= created_at),

    CONSTRAINT ck_refresh_token_family_revocation_state
        CHECK (
            (revoked_at IS NULL AND revocation_reason IS NULL)
                OR
            (revoked_at IS NOT NULL AND revocation_reason IS NOT NULL)
        ),

    CONSTRAINT ck_refresh_token_family_revocation_reason
        CHECK (
            revocation_reason IS NULL
                OR revocation_reason IN ('LOGOUT', 'TOKEN_REUSE', 'SECURITY_ACTION')
        )
);

CREATE INDEX ix_refresh_token_family_active_user
    ON refresh_token_family (user_id)
    WHERE revoked_at IS NULL;

CREATE TABLE refresh_token
(
    id          UUID        NOT NULL,
    family_id   UUID        NOT NULL,
    token_hash  BYTEA       NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,

    CONSTRAINT pk_refresh_token
        PRIMARY KEY (id),

    CONSTRAINT fk_refresh_token_family
        FOREIGN KEY (family_id)
            REFERENCES refresh_token_family (id)
            ON DELETE RESTRICT,

    CONSTRAINT uq_refresh_token_hash
        UNIQUE (token_hash),

    CONSTRAINT ck_refresh_token_hash_length
        CHECK (octet_length(token_hash) = 32),

    CONSTRAINT ck_refresh_token_expiry
        CHECK (expires_at > created_at),

    CONSTRAINT ck_refresh_token_consumed_at
        CHECK (consumed_at IS NULL OR consumed_at >= created_at)
);

CREATE UNIQUE INDEX uq_refresh_token_family_unconsumed
    ON refresh_token (family_id)
    WHERE consumed_at IS NULL;

CREATE INDEX ix_refresh_token_family_created_at
    ON refresh_token (family_id, created_at DESC);
