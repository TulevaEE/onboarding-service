ALTER TABLE investment_transaction_command ADD COLUMN exclude_isins jsonb NOT NULL DEFAULT '[]';
