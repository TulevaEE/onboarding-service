ALTER TABLE payment
  ADD CONSTRAINT payment_internal_reference_key UNIQUE (internal_reference);
