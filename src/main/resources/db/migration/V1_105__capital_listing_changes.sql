ALTER TABLE listing DROP COLUMN price_per_unit;
ALTER TABLE listing ADD COLUMN total_price NUMERIC(14, 2) NOT NULL;
