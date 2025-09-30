ALTER TABLE saving_fund_payment DROP COLUMN end_to_end_id;
ALTER TABLE saving_fund_payment ALTER COLUMN external_id DROP NOT NULL;