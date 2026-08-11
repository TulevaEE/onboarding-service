DROP INDEX ledger.ux_account_owner_name;

ALTER TABLE ledger.account
  ADD CONSTRAINT ux_account_owner_name
    UNIQUE NULLS NOT DISTINCT (owner_party_id, name, purpose, asset_type, account_type);
