ALTER TABLE mandate
  ADD COLUMN details jsonb NOT NULL DEFAULT '{}'::jsonb;


ALTER TABLE mandate
  ADD COLUMN mandate_type VARCHAR(255) DEFAULT 'UNKNOWN';
