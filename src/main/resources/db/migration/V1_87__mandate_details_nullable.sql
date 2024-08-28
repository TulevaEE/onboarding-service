ALTER TABLE mandate ALTER COLUMN details DROP NOT NULL;

UPDATE mandate
SET details = null
WHERE details = '{}';
