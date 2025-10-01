ALTER TABLE saving_fund_payment ADD COLUMN status TEXT;
UPDATE saving_fund_payment SET status='CREATED' WHERE status IS NULL;
ALTER TABLE saving_fund_payment ALTER COLUMN status SET NOT NULL;
ALTER TABLE saving_fund_payment ADD COLUMN status_changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
