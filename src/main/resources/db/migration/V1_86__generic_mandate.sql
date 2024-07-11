ALTER TABLE mandate
  ADD COLUMN details jsonb NOT NULL DEFAULT '{}'::jsonb;
