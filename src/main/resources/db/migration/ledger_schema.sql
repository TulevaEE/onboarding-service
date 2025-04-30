/* TODO remove*/
DROP SCHEMA IF EXISTS ledger CASCADE;

CREATE SCHEMA ledger;
/* TODO permissions */

CREATE TYPE ledger.account_type AS ENUM ('ASSET', 'LIABILITY', 'INCOME', 'EXPENSE');
CREATE TYPE ledger.service_account_type AS ENUM ('DEPOSIT_EUR', 'EMISSION_UNIT'); /*TODO add/rename here */
CREATE TYPE ledger.asset_category AS ENUM ('EUR', 'UNIT');
CREATE TYPE ledger.party_type AS ENUM ('USER', 'LEGAL_ENTITY');

CREATE TABLE ledger.asset_type(
    code VARCHAR(255) PRIMARY KEY,
    name TEXT NOT NULL,
    precision INTEGER NOT NULL,
    category ledger.asset_category NOT NULL
);


CREATE TABLE ledger.transaction_type(
    code VARCHAR(255) PRIMARY KEY,
    description TEXT NOT NULL
);


CREATE TABLE ledger.party(
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  type ledger.party_type NOT NULL,
  name TEXT NOT NULL,
  details JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


CREATE TABLE ledger.account(
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    name TEXT NOT NULL,
    service_account_type ledger.service_account_type,
    type ledger.account_type NOT NULL,
    owner_party_id UUID REFERENCES ledger.party,
    asset_type_code VARCHAR(255) NOT NULL references ledger.asset_type,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    CONSTRAINT owner_party_or_service_account CHECK (
      NOT (
        (owner_party_id IS NOT NULL AND service_account_type IS NOT NULL)
        OR (owner_party_id IS NULL and service_account_type IS NULL)
      )
    )
    /*UNIQUE (service_account, type) TODO can only have 1 service account of type*/
);


CREATE TABLE ledger.transaction(
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    description TEXT NOT NULL,
    transaction_type_id VARCHAR(255) NOT NULL REFERENCES ledger.transaction_type,
    transaction_date TIMESTAMPTZ NOT NULL,
    metadata JSONB NOT NULL, /* TODO lock this down – or keep this a JSONB here with very heavy validations in application layer */
    /*event_log_id INTEGER NOT NULL references public.event_log, TODO add this back*/
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE ledger.entry(
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    account_id UUID NOT NULL references ledger.account,
    transaction_id UUID NOT NULL references ledger.transaction,
    amount NUMERIC NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


INSERT INTO ledger.asset_type (code, name, precision, category) VALUES
                                     ('EUR', 'Euro', 2, 'EUR'),
                                     ('UNIT', 'Stock unit for EE_TEST_ISIN', 5, 'EUR');


INSERT INTO ledger.transaction_type (code, description) VALUES
                                                          ('TEST', 'Test transaction');

/* TODO mock service accounts */
INSERT INTO ledger.account (name, service_account_type, type, asset_type_code) VALUES
                ('Tuleva cash deposit', 'DEPOSIT_EUR', 'INCOME', 'EUR'),
                ('Tuleva unit emission', 'EMISSION_UNIT', 'ASSET', 'UNIT');

