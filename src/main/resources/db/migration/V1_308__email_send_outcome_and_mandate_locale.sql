ALTER TABLE email ADD COLUMN attempts integer;
ALTER TABLE email ADD COLUMN last_error text;
ALTER TABLE mandate ADD COLUMN locale text;
