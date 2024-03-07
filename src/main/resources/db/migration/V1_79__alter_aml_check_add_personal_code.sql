ALTER TABLE aml_check
  ADD COLUMN personal_code CHAR(11);

UPDATE aml_check
SET personal_code = (SELECT personal_code
                     FROM users
                     WHERE aml_check.user_id = users.id);

ALTER TABLE aml_check
  ALTER COLUMN personal_code SET NOT NULL;

CREATE INDEX aml_check_personal_code_index ON aml_check (personal_code);

ALTER TABLE aml_check
  DROP CONSTRAINT IF EXISTS aml_check_user_id_fkey;

DROP INDEX IF EXISTS aml_check_user_id_index;

ALTER TABLE aml_check
  DROP COLUMN user_id;
