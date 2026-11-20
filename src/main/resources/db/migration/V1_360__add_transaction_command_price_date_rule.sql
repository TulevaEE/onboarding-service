ALTER TABLE investment_transaction_command ADD COLUMN price_date_rule text NOT NULL DEFAULT 'FRESHEST_PER_INSTRUMENT';
