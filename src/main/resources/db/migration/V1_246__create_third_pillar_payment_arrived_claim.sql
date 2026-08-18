CREATE TABLE third_pillar_payment_arrived_claim
(
    personal_code TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT third_pillar_payment_arrived_claim_pk PRIMARY KEY (personal_code)
);
