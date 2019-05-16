ALTER TABLE fund
  ADD COLUMN name_english text;
ALTER TABLE fund
  ADD COLUMN name_estonian text;

UPDATE fund
SET name_estonian = name
WHERE name_estonian IS NULL;

UPDATE fund
SET name_english = name
WHERE name_english IS NULL;

ALTER TABLE fund
  ALTER COLUMN name_english SET NOT NULL;

ALTER TABLE fund
  ALTER COLUMN name_english SET NOT NULL;

ALTER TABLE fund DROP COLUMN name;