ALTER TABLE savings_fund_onboarding
  ADD COLUMN status_changed_at TIMESTAMPTZ;

UPDATE savings_fund_onboarding
SET status_changed_at = created_at
WHERE status_changed_at IS NULL;

ALTER TABLE savings_fund_onboarding
  ALTER COLUMN status_changed_at SET NOT NULL;
