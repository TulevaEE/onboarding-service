ALTER TABLE investment_transaction_command ADD COLUMN prices jsonb NOT NULL DEFAULT '[]';
