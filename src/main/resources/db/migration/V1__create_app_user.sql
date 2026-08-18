CREATE TABLE app_user
(
    id               UUID         NOT NULL,

    email            VARCHAR(320) NOT NULL,
    password_hash    VARCHAR(255) NOT NULL,
    status           VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_app_user
        PRIMARY KEY (id),

    CONSTRAINT uq_app_user_email
        UNIQUE (email),

    CONSTRAINT ck_app_user_status
        CHECK (status IN ('ACTIVE', 'DISABLED')),

    CONSTRAINT ck_app_user_email_not_blank
        CHECK (btrim(email) <> ''),

    CONSTRAINT ck_app_user_email
        CHECK (email = lower(btrim(email))),

    CONSTRAINT ck_app_user_password_hash_not_blank
        CHECK (btrim(password_hash) <> '')
);