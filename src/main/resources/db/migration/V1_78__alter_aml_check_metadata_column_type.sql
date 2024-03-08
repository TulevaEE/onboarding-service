ALTER TABLE aml_check
  ALTER COLUMN metadata DROP DEFAULT;

ALTER TABLE aml_check
  ALTER COLUMN metadata TYPE jsonb USING metadata::jsonb;

ALTER TABLE aml_check
  ALTER COLUMN metadata SET DEFAULT '{}'::jsonb;
