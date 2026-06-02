CREATE TABLE aml_third_pillar_alert (
    id             bigserial   NOT NULL,
    transaction_id bigint      NOT NULL,
    alert_type     text        NOT NULL,
    alerted_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_aml_third_pillar_alert PRIMARY KEY (id),
    CONSTRAINT uk_aml_third_pillar_alert UNIQUE (transaction_id, alert_type)
);

CREATE INDEX ix_aml_third_pillar_alert_transaction_id ON aml_third_pillar_alert (transaction_id);
