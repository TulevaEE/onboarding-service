CREATE TABLE outgoing_payment (
    id                bigserial      NOT NULL,
    end_to_end_id     text           NOT NULL,
    payment_type      text           NOT NULL,
    source_id         uuid,
    batch_id          uuid,
    remitter_iban     text           NOT NULL,
    beneficiary_iban  text           NOT NULL,
    amount            numeric(19, 2) NOT NULL,
    currency          text           NOT NULL DEFAULT 'EUR',
    body_hash         text           NOT NULL,
    status            text           NOT NULL,
    failure_reason    text,
    attempted_at      timestamptz    NOT NULL DEFAULT now(),
    resolved_at       timestamptz,
    CONSTRAINT outgoing_payment_pkey PRIMARY KEY (id),
    CONSTRAINT outgoing_payment_end_to_end_id_key UNIQUE (end_to_end_id)
);

COMMENT ON TABLE outgoing_payment IS
    'What we actually sent to the bank. Written before the HTTP call, in its own transaction, so a '
    'commit failure after the bank accepted the file still leaves a record.';
COMMENT ON COLUMN outgoing_payment.batch_id IS
    'Groups a fund-to-withdrawal transfer with the payouts it funds, so transfer = sum(payouts) is computable.';
COMMENT ON COLUMN outgoing_payment.status IS
    'ATTEMPTED until the call returns. SUBMITTED on success, FAILED on a definitive rejection. Left '
    'ATTEMPTED when the call times out or the process dies: may or may not have executed.';

CREATE INDEX outgoing_payment_status_idx ON outgoing_payment (status, attempted_at);
CREATE INDEX outgoing_payment_batch_idx ON outgoing_payment (batch_id);
CREATE INDEX outgoing_payment_pending_idx ON outgoing_payment (attempted_at DESC);
