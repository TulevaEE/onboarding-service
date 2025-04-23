CREATE SCHEMA ledger;
/* TODO permissions */

CREATE TYPE ledger.account_type AS ENUM ('ASSET', 'LIABILITY', 'INCOME', 'EXPENSE');
CREATE TYPE ledger.service_account_type AS ENUM ('DEPOSIT_EUR'); /*TODO add/rename here */
CREATE TYPE ledger.asset_category AS ENUM ('EUR', 'UNIT');
CREATE TYPE ledger.party_type AS ENUM ('USER', 'LEGAL_ENTITY');

CREATE TABLE ledger.asset_type(
    code VARCHAR(255) PRIMARY KEY,
    name TEXT NOT NULL,
    precision INTEGER NOT NULL,
    category asset_category NOT NULL
);


CREATE TABLE ledger.transaction_type(
    code VARCHAR(255) PRIMARY KEY,
    description TEXT NOT NULL
);


CREATE TABLE ledger.party(
  id UUID PRIMARY KEY,
  type ledger.party_type NOT NULL,
  name TEXT NOT NULL,
  details JSONB NOT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW()
);


CREATE TABLE ledger.account(
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    service_account_type ledger.service_account_type,
    type ledger.account_type NOT NULL,
    owner_party_id UUID REFERENCES ledger.party,
    asset_type_code VARCHAR(255) NOT NULL references ledger.asset_type,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    /* cannot be owned by party and a service account at the same time */
    CHECK (
      (owner_party_id IS NOT NULL AND service_account_type IS NOT NULL)
      OR (owner_party_id IS NULL and service_account_type IS NULL)
    ),
    /* cannot be a service account and a managed account at the same time */
    CHECK (
      (service_account_type IS NOT NULL AND owner_party_id IS NULL)
      OR (owner_party_id IS NULL AND owner_party_id IS NULL)
    )
    /*UNIQUE (service_account, type) TODO can only have 1 service account of type*/
);


CREATE TABLE ledger.transaction(
    id UUID PRIMARY KEY,
    description TEXT NOT NULL,
    transaction_type_id VARCHAR(255) NOT NULL REFERENCES ledger.transaction_type,
    transaction_date TIMESTAMPTZ NOT NULL,
    metadata JSONB NOT NULL, /* TODO lock this down – or keep this a JSONB here with very heavy validations in application layer */
    event_log_id INTEGER NOT NULL references public.event_log,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE ledger.entry(
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL references ledger.account,
    transaction_id UUID NOT NULL references ledger.transaction,
    amount NUMERIC NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);


/* TODO insert service account */
