/*
  This temporary migration is to be able to run integration tests on H2, while ledger schema is still under development
   and not in a production migration.
  TODO After schema is in production, remove this migration.
 */


/* TODO remove*/
DROP SCHEMA IF EXISTS ledger CASCADE;

CREATE SCHEMA ledger;
/* TODO permissions */

CREATE TYPE ledger.account_type AS ENUM ('ASSET', 'LIABILITY', 'INCOME', 'EXPENSE');
CREATE TYPE ledger.account_purpose AS ENUM ('USER_ACCOUNT', 'SYSTEM_ACCOUNT');
CREATE TYPE ledger.party_type AS ENUM ('USER', 'LEGAL_ENTITY');
CREATE TYPE ledger.asset_type AS ENUM ('EUR', 'FUND_UNIT');
CREATE TYPE ledger.transaction_type AS ENUM ('TRANSFER');


CREATE TABLE ledger.party
(
  id         UUID                       DEFAULT gen_random_uuid() PRIMARY KEY,
  party_type ledger.party_type NOT NULL,
  owner_id   TEXT              NOT NULL,
  details    JSONB             NOT NULL,
  created_at TIMESTAMP         NOT NULL DEFAULT NOW()
);


CREATE TABLE ledger.account
(
  id             UUID                            DEFAULT gen_random_uuid() PRIMARY KEY,
  name           TEXT,
  purpose        ledger.account_purpose NOT NULL,
  account_type   ledger.account_type    NOT NULL,
  owner_party_id UUID REFERENCES ledger.party,
  asset_type     ledger.asset_type      NOT NULL,
  created_at     TIMESTAMP              NOT NULL DEFAULT NOW(),
  CONSTRAINT account_ownership_check CHECK (
    (purpose = 'USER_ACCOUNT' AND owner_party_id IS NOT NULL)
      OR
    (purpose = 'SYSTEM_ACCOUNT' AND owner_party_id IS NULL)
    )
  /*UNIQUE (service_account, type) TODO can only have 1 service account of type*/
);


CREATE TABLE ledger.transaction
(
  id               UUID                             DEFAULT gen_random_uuid() PRIMARY KEY,
  transaction_type ledger.transaction_type NOT NULL,
  transaction_date TIMESTAMP               NOT NULL,
  metadata         JSONB                   NOT NULL, /* TODO lock this down – or keep this a JSONB here with very heavy validations in application layer */
  /*event_log_id INTEGER NOT NULL references public.event_log, TODO add this back*/
  created_at       TIMESTAMP               NOT NULL DEFAULT NOW()
);

CREATE TABLE ledger.entry
(
  id             UUID                       DEFAULT gen_random_uuid() PRIMARY KEY,
  account_id     UUID              NOT NULL references ledger.account,
  transaction_id UUID              NOT NULL references ledger.transaction,
  amount         NUMERIC           NOT NULL,
  asset_type     ledger.asset_type NOT NULL,
  created_at     TIMESTAMP         NOT NULL DEFAULT NOW()
);



