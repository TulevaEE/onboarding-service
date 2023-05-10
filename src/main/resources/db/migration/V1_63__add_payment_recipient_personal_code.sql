ALTER TABLE payment
  ADD COLUMN recipient_personal_code CHAR(11);

UPDATE payment
SET recipient_personal_code = (SELECT personal_code
                               FROM users
                               WHERE payment.user_id = users.id);

ALTER TABLE payment
  ALTER COLUMN recipient_personal_code SET NOT NULL;

