ALTER TABLE audit_log
  ALTER COLUMN data TYPE jsonb;

ALTER TABLE audit_log
  RENAME TO event_log;
