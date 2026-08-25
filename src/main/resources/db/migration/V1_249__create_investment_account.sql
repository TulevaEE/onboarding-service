CREATE TABLE investment_account
(
    personal_code TEXT NOT NULL,
    iban          TEXT NOT NULL,
    CONSTRAINT investment_account_pk PRIMARY KEY (personal_code)
);
