ALTER TABLE fund ADD COLUMN short_name VARCHAR(255);
UPDATE fund SET short_name = '' WHERE short_name IS NULL;
ALTER TABLE fund ALTER COLUMN short_name SET NOT NULL;
