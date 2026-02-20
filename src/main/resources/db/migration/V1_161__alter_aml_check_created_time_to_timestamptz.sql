-- Hibernate 7 native queries return LocalDateTime for TIMESTAMP columns,
-- but the Java code expects Instant which requires TIMESTAMPTZ.
ALTER TABLE aml_check ALTER COLUMN created_time TYPE TIMESTAMP WITH TIME ZONE;
