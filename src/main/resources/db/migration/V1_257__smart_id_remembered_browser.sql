-- A browser that has completed a device link Smart-ID login may start a push login for the
-- account it verified. The browser holds nothing but an unguessable token; everything the push
-- needs lives here, so the marker can be revoked, and so a copied cookie reveals nothing.
--
-- verified_at is when a device link login last proved the person holds the device, and the
-- expiry is measured from it. A push login carries it forward rather than extending it, so the
-- browser must verify with a QR or same-device link again every 90 days.
CREATE TABLE smart_id_remembered_browser (
    id              bigserial   NOT NULL,
    token_hash      text        NOT NULL,
    personal_code   text        NOT NULL,
    document_number text        NOT NULL,
    first_name      text        NOT NULL,
    last_name       text        NOT NULL,
    verified_at     timestamptz NOT NULL,
    expires_at      timestamptz NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_smart_id_remembered_browser PRIMARY KEY (id),
    CONSTRAINT uk_smart_id_remembered_browser_token UNIQUE (token_hash)
);

CREATE INDEX ix_smart_id_remembered_browser_personal_code
    ON smart_id_remembered_browser (personal_code);

CREATE INDEX ix_smart_id_remembered_browser_expires_at
    ON smart_id_remembered_browser (expires_at);
