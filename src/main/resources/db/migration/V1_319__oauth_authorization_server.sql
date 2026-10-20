CREATE TABLE oauth_subject
(
    id            UUID        NOT NULL,
    client_id     TEXT        NOT NULL,
    personal_code TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,

    CONSTRAINT oauth_subject_pk PRIMARY KEY (id),
    CONSTRAINT oauth_subject_client_person_uq UNIQUE (client_id, personal_code)
);

CREATE TABLE oauth_pending_authorization
(
    id                    UUID        NOT NULL,
    client_id             TEXT        NOT NULL,
    redirect_uri          TEXT        NOT NULL,
    state                 TEXT,
    scopes                TEXT        NOT NULL,
    code_challenge        TEXT        NOT NULL,
    code_challenge_method TEXT        NOT NULL,
    browser_binding_hash  TEXT        NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    consumed_at           TIMESTAMPTZ,

    CONSTRAINT oauth_pending_authorization_pk PRIMARY KEY (id)
);

CREATE TABLE oauth_grant
(
    id                            UUID        NOT NULL,
    client_id                     TEXT        NOT NULL,
    subject_id                    UUID        NOT NULL,
    personal_code                 TEXT        NOT NULL,
    scopes                        TEXT        NOT NULL,
    redirect_uri                  TEXT        NOT NULL,
    state                         TEXT,
    code_challenge                TEXT        NOT NULL,
    code_challenge_method         TEXT        NOT NULL,
    granted_at                    TIMESTAMPTZ NOT NULL,
    expires_at                    TIMESTAMPTZ NOT NULL,
    last_refreshed_at             TIMESTAMPTZ NOT NULL,
    authorization_code_hash       TEXT,
    authorization_code_issued_at  TIMESTAMPTZ,
    authorization_code_expires_at TIMESTAMPTZ,
    authorization_code_used       BOOLEAN     NOT NULL,
    access_token_hash             TEXT,
    access_token_issued_at        TIMESTAMPTZ,
    access_token_expires_at       TIMESTAMPTZ,
    refresh_token_hash            TEXT,
    refresh_token_issued_at       TIMESTAMPTZ,
    refresh_token_expires_at      TIMESTAMPTZ,
    revoked_at                    TIMESTAMPTZ,
    revocation_reason             TEXT,

    CONSTRAINT oauth_grant_pk PRIMARY KEY (id),
    CONSTRAINT oauth_grant_subject_fk FOREIGN KEY (subject_id) REFERENCES oauth_subject (id)
);

CREATE INDEX idx_oauth_grant_subject ON oauth_grant (subject_id);
CREATE INDEX idx_oauth_grant_client ON oauth_grant (client_id);
CREATE INDEX idx_oauth_grant_authorization_code_hash ON oauth_grant (authorization_code_hash);
CREATE INDEX idx_oauth_grant_access_token_hash ON oauth_grant (access_token_hash);
CREATE INDEX idx_oauth_grant_refresh_token_hash ON oauth_grant (refresh_token_hash);

CREATE TABLE oauth_spent_refresh_token
(
    token_hash TEXT        NOT NULL,
    grant_id   UUID        NOT NULL,
    spent_at   TIMESTAMPTZ NOT NULL,

    CONSTRAINT oauth_spent_refresh_token_pk PRIMARY KEY (token_hash),
    CONSTRAINT oauth_spent_refresh_token_grant_fk FOREIGN KEY (grant_id) REFERENCES oauth_grant (id)
);

CREATE INDEX idx_oauth_spent_refresh_token_grant ON oauth_spent_refresh_token (grant_id);

CREATE TABLE oauth_client_suspension
(
    client_id    TEXT        NOT NULL,
    suspended_at TIMESTAMPTZ NOT NULL,
    reason       TEXT        NOT NULL,

    CONSTRAINT oauth_client_suspension_pk PRIMARY KEY (client_id)
);
