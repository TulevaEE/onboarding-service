CREATE SCHEMA ledger;
/* TODO permissions */

CREATE TYPE currency AS ENUM ('EUR', 'EE_TEST_FUND_ISIN'); /* rename from isin? */
CREATE TYPE ledger_type AS ENUM ('ASSET', 'LIABILITY', 'INCOME', 'EXPENSE');
CREATE TYPE managed_account_type AS ENUM ('COMPANY', 'CHILD');

CREATE TABLE ledger.account(
    id UUID PRIMARY KEY,
    user_id INTEGER REFERENCES public.users,
    service_account BOOLEAN, /* TODO use specific enum types here, or only two potential values. service_account = FALSE is a valid type currently but shouldn't */
    managed_account managed_account_type,
    managee_id TEXT, /* child's personal code, or company's registration code */
    name TEXT NOT NULL,
    currency currency NOT NULL,
    type ledger_type NOT NULL,
    created_time TIMESTAMPTZ DEFAULT NOW(),
    /* cannot be owned by user and a service account at the same time */
    CHECK (
      (user_id IS NOT NULL AND service_account IS NOT NULL)
      OR (user_id IS NULL and service_account IS NULL)
    ),
    /* cannot be a service account and a managed account at the same time */
    CHECK (
      (service_account IS NOT NULL AND managed_account IS NULL)
      OR (managed_account IS NULL AND managee_id IS NULL)
    ),
    UNIQUE (user_id, managee_id) /*can only have 1 account per managee */
    /*UNIQUE (service_account, type) TODO can only have 1 service account of type*/
);


CREATE TABLE ledger.transaction(
    id UUID PRIMARY KEY,
    metadata JSONB NOT NULL, /* TODO lock this down – or keep this a JSONB here with very heavy validations in application layer */
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
