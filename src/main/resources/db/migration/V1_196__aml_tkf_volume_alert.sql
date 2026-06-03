CREATE TABLE aml_tkf_volume_alert (
    id          bigserial   NOT NULL,
    personal_id text        NOT NULL,
    alert_type  text        NOT NULL,
    direction   text        NOT NULL,
    window_key  text        NOT NULL,
    alerted_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_aml_tkf_volume_alert PRIMARY KEY (id),
    CONSTRAINT uk_aml_tkf_volume_alert UNIQUE (personal_id, alert_type, direction, window_key)
);

CREATE INDEX ix_aml_tkf_volume_alert_personal_id ON aml_tkf_volume_alert (personal_id);
