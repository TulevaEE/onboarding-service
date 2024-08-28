ALTER TABLE mandate
  ADD COLUMN IF NOT EXISTS details jsonb NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE mandate ALTER COLUMN details DROP NOT NULL;

UPDATE mandate
SET details = null
WHERE details = '{}';
