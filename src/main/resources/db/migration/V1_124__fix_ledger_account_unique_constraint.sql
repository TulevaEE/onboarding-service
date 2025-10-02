DROP INDEX IF EXISTS ledger.ux_account_system_name;

CREATE UNIQUE INDEX ux_account_owner_name
  ON ledger.account (owner_party_id, name, purpose, asset_type, account_type);
