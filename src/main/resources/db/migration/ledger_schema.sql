CREATE SCHEMA ledger;
/* TODO permissions */

CREATE TYPE currency AS ENUM ('EUR', 'EE_TEST_FUND_ISIN'); /* rename from isin? */
CREATE TYPE ledger_type AS ENUM ('ASSET', 'LIABILITY', 'INCOME', 'EXPENSE');

CREATE TABLE ledger.account(
    id UUID PRIMARY KEY,
    user_id INTEGER REFERENCES public.users,
    service_account BOOLEAN,
    name TEXT NOT NULL,
    currency currency NOT NULL,
    type ledger_type NOT NULL,
    created_time TIMESTAMPTZ DEFAULT NOW(),
    CHECK (
      (user_id IS NOT NULL AND service_account IS NOT NULL)
      OR (user_id IS NULL and service_account IS NULL)
    ),
    CHECK (
      (type IN ('ASSET', 'LIABILITY') AND currency != 'EE_TEST_FUND_ISIN')
      OR (type IN ('INCOME', 'EXPENSE') AND currency != 'EUR')
    ),
    UNIQUE (service_account, type)
);


CREATE TABLE ledger.transaction(
    id UUID PRIMARY KEY,
    metadata JSONB NOT NULL,
    created_time TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE ledger.entry(
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL references ledger.account,
    transaction_id UUID NOT NULL references ledger.transaction,
    amount NUMERIC NOT NULL,
    created_time TIMESTAMPTZ DEFAULT NOW()
);


/* TODO insert service account */
