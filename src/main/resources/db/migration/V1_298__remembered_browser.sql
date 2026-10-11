CREATE TABLE remembered_browser (
    id                                     bigserial   NOT NULL,
    token_hash                             text        NOT NULL,
    expires_at                             timestamptz NOT NULL,
    smart_id_notification_login_started_at timestamptz,
    mobile_id_login_started_at             timestamptz,
    created_at                             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_remembered_browser PRIMARY KEY (id),
    CONSTRAINT uk_remembered_browser_token UNIQUE (token_hash)
);

CREATE INDEX ix_remembered_browser_expires_at
    ON remembered_browser (expires_at);

CREATE TABLE remembered_smart_id_account (
    id              bigserial   NOT NULL,
    browser_id      bigint      NOT NULL,
    personal_code   text        NOT NULL,
    document_number text        NOT NULL,
    first_name      text        NOT NULL,
    last_name       text        NOT NULL,
    verified_at     timestamptz NOT NULL,
    expires_at      timestamptz NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_remembered_smart_id_account PRIMARY KEY (id),
    CONSTRAINT uk_remembered_smart_id_account_browser UNIQUE (browser_id),
    CONSTRAINT fk_remembered_smart_id_account_browser
        FOREIGN KEY (browser_id) REFERENCES remembered_browser (id) ON DELETE CASCADE
);

CREATE INDEX ix_remembered_smart_id_account_personal_code
    ON remembered_smart_id_account (personal_code);

CREATE INDEX ix_remembered_smart_id_account_expires_at
    ON remembered_smart_id_account (expires_at);

CREATE TABLE remembered_mobile_id_phone (
    id            bigserial   NOT NULL,
    browser_id    bigint      NOT NULL,
    personal_code text        NOT NULL,
    phone_number  text        NOT NULL,
    expires_at    timestamptz NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_remembered_mobile_id_phone PRIMARY KEY (id),
    CONSTRAINT uk_remembered_mobile_id_phone_browser_person UNIQUE (browser_id, personal_code),
    CONSTRAINT fk_remembered_mobile_id_phone_browser
        FOREIGN KEY (browser_id) REFERENCES remembered_browser (id) ON DELETE CASCADE
);

CREATE INDEX ix_remembered_mobile_id_phone_expires_at
    ON remembered_mobile_id_phone (expires_at);
