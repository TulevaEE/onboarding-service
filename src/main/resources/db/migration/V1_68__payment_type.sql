ALTER TABLE payment
  ADD COLUMN payment_type VARCHAR(255) DEFAULT 'SINGLE';

UPDATE payment
SET payment_type = (
  SELECT CASE
           WHEN u.personal_code != payment.recipient_personal_code THEN 'GIFT'
           ELSE 'SINGLE'
           END
  FROM users AS u
  WHERE payment.user_id = u.id
);

UPDATE payment
SET payment_type = 'SINGLE'
WHERE payment_type IS NULL;

ALTER TABLE payment
  ALTER COLUMN payment_type SET NOT NULL;

ALTER TABLE payment
  ALTER COLUMN payment_type DROP DEFAULT;
