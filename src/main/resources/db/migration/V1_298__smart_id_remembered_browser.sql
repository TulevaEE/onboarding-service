CREATE TABLE smart_id_remembered_browser (
    id              bigserial   NOT NULL,
    token_hash      text        NOT NULL,
    personal_code   text        NOT NULL,
    document_number text        NOT NULL,
    first_name      text        NOT NULL,
    last_name       text        NOT NULL,
    verified_at     timestamptz NOT NULL,
    expires_at      timestamptz NOT NULL,
    notification_login_started_at timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_smart_id_remembered_browser PRIMARY KEY (id),
    CONSTRAINT uk_smart_id_remembered_browser_token UNIQUE (token_hash)
);

CREATE INDEX ix_smart_id_remembered_browser_personal_code
    ON smart_id_remembered_browser (personal_code);

CREATE INDEX ix_smart_id_remembered_browser_expires_at
    ON smart_id_remembered_browser (expires_at);
