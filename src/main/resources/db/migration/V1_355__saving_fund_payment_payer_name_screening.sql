ALTER TABLE saving_fund_payment ADD COLUMN payer_name_screening text;

ALTER TABLE saving_fund_payment
  ADD CONSTRAINT saving_fund_payment_payer_name_screening_check
  CHECK (payer_name_screening IN ('CLEAR', 'PEP_HIT', 'SANCTION_HIT'));

COMMENT ON COLUMN saving_fund_payment.payer_name_screening IS
  'Sanctions and PEP screening by name of a payer who sent no personal or registry code and whose name is less than 90% similar to the client''s; NULL when the payer was not screened by name.';
