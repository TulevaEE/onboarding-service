DELETE FROM investment_position_limit
WHERE isin IS NULL;

ALTER TABLE investment_position_limit ALTER COLUMN isin SET NOT NULL;
