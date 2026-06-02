CREATE TABLE saving_fund_iban_whitelist (
    id uuid DEFAULT gen_random_uuid(),
    party_type text NOT NULL,
    party_code text NOT NULL,
    iban       text NOT NULL,
    comment    text,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_saving_fund_iban_whitelist PRIMARY KEY (id),
    CONSTRAINT uq_saving_fund_iban_whitelist UNIQUE (party_type, party_code, iban)
);

CREATE INDEX idx_saving_fund_iban_whitelist_party ON saving_fund_iban_whitelist(party_type, party_code);
