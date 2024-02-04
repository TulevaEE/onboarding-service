ALTER TABLE email
  ADD COLUMN updated_date TIMESTAMP;

UPDATE email
SET updated_date = created_date;

ALTER TABLE email
  ALTER COLUMN updated_date SET NOT NULL;
