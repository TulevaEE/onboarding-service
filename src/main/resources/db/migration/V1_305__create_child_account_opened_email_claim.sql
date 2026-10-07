CREATE TABLE child_account_opened_email_claim
(
    child_code TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT child_account_opened_email_claim_pk PRIMARY KEY (child_code)
);
