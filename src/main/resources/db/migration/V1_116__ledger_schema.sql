CREATE SCHEMA ledger;

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
);


CREATE TABLE ledger.transaction
(
  id               UUID                             DEFAULT gen_random_uuid() PRIMARY KEY,
  transaction_type ledger.transaction_type NOT NULL,
  transaction_date TIMESTAMP               NOT NULL,
  metadata         JSONB                   NOT NULL,
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

CREATE INDEX idx_account_owner_party_id
  ON ledger.account (owner_party_id);
CREATE UNIQUE INDEX ux_account_system_name
  ON ledger.account (name, purpose, asset_type, account_type);
CREATE INDEX idx_transaction_date
  ON ledger.transaction (transaction_date);
CREATE INDEX idx_entry_account_id
  ON ledger.entry (account_id);
CREATE INDEX idx_entry_transaction_id
  ON ledger.entry (transaction_id);
CREATE INDEX idx_entry_txn_acct
  ON ledger.entry (transaction_id, account_id);
CREATE UNIQUE INDEX ux_party_type_owner
  ON ledger.party (party_type, owner_id);


