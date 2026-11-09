CREATE TABLE partner_spent_handover_token
(
  issuer   TEXT        NOT NULL,
  token_id TEXT        NOT NULL,
  spent_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT partner_spent_handover_token_pk PRIMARY KEY (issuer, token_id)
);
