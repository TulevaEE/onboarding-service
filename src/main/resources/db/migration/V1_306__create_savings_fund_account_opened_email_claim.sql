CREATE TABLE savings_fund_account_opened_email_claim
(
    code       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT savings_fund_account_opened_email_claim_pk PRIMARY KEY (code)
);
