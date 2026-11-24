ALTER TABLE investment_transaction_command ADD COLUMN confirmed_price_deviations jsonb NOT NULL DEFAULT '[]';
