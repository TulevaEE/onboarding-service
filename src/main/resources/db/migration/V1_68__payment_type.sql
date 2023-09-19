ALTER TABLE payment
  ADD COLUMN payment_type VARCHAR(255);

UPDATE payment
  SET payment_type = 'SINGLE';

ALTER TABLE payment
  ALTER COLUMN payment_type SET NOT NULL;

