ALTER TABLE fund ADD COLUMN equity_share NUMERIC(10, 8);
UPDATE fund SET equity_share = 0.0 WHERE equity_share IS NULL;
ALTER TABLE fund ALTER COLUMN equity_share SET NOT NULL;