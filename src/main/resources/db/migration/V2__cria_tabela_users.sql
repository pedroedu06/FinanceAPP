CREATE TABLE users (
    id                    UUID                     PRIMARY KEY,
    version               BIGINT                   NOT NULL,
    name                  VARCHAR(120)             NOT NULL,
    email                 VARCHAR(254)             NOT NULL,
    password_hash         VARCHAR(255)             NOT NULL,
    pay_day               INTEGER                  NOT NULL,
    token_version         INTEGER                  NOT NULL DEFAULT 0,
    failed_login_attempts INTEGER                  NOT NULL DEFAULT 0,
    locked_until          TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_pay_day CHECK (pay_day BETWEEN 1 AND 31)
);
