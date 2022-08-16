ALTER TABLE audit_log
  ALTER COLUMN data TYPE jsonb USING data::jsonb;

ALTER TABLE audit_log
  RENAME TO event_log;
