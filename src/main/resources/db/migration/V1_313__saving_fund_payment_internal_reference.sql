ALTER TABLE saving_fund_payment ADD COLUMN internal_reference UUID;

ALTER TABLE saving_fund_payment
  ADD CONSTRAINT saving_fund_payment_internal_reference_key UNIQUE (internal_reference);
