ALTER TABLE fund ADD COLUMN status VARCHAR(255);
UPDATE fund SET status = 'ACTIVE' WHERE status IS NULL;
ALTER TABLE fund ALTER COLUMN status SET NOT NULL;
