ALTER TABLE investment_transaction_command ALTER COLUMN manual_adjustments SET DEFAULT JSON '{}';
ALTER TABLE investment_transaction_command ALTER COLUMN exclude_isins SET DEFAULT JSON '[]';
