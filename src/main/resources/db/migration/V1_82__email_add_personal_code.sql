ALTER TABLE email
  ADD COLUMN personal_code CHAR(11);

UPDATE email
SET personal_code = (SELECT personal_code
                     FROM users
                     WHERE email.user_id = users.id);

ALTER TABLE email
  ALTER COLUMN personal_code SET NOT NULL;

CREATE INDEX email_personal_code_index ON email (personal_code);

ALTER TABLE email
  DROP CONSTRAINT IF EXISTS scheduled_email_user_id_fkey;

DROP INDEX IF EXISTS scheduled_email_user_id_index;

ALTER TABLE email
  DROP COLUMN user_id;

ALTER TABLE email
  ALTER COLUMN mandrill_message_id DROP NOT NULL;

ALTER TABLE email
  ALTER COLUMN updated_date SET DEFAULT NOW();
