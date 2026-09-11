CREATE TABLE payment_check_event (
    id           bigserial   NOT NULL,
    check_type   text        NOT NULL,
    severity     text        NOT NULL,
    external_key text        NOT NULL,
    detail       text        NOT NULL,
    alert_failed boolean     NOT NULL DEFAULT false,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT payment_check_event_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE payment_check_event IS
    'Findings from the payment detectors. Persisted rather than kept in memory because the current '
    'day''s bank statement is re-fetched every five minutes, so the same entry is seen many times '
    'and would otherwise re-alert all afternoon.';
COMMENT ON COLUMN payment_check_event.external_key IS
    'Identifies the thing the finding is about - a bank entry id, an endToEndId - so the same '
    'finding is recognised across re-reads.';
COMMENT ON COLUMN payment_check_event.alert_failed IS
    'Set when the notification could not be delivered, so a finding first seen during a chat outage '
    'alerts again instead of silently becoming the new baseline.';

CREATE UNIQUE INDEX payment_check_event_dedupe_idx
    ON payment_check_event (check_type, external_key);
CREATE INDEX payment_check_event_created_idx ON payment_check_event (created_at DESC);
