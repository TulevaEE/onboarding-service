CREATE INDEX IF NOT EXISTS event_log_type_principal_timestamp_index
  ON event_log (type, principal, timestamp);
