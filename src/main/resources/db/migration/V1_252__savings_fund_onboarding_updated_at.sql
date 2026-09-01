ALTER TABLE savings_fund_onboarding
  ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

UPDATE savings_fund_onboarding
SET updated_at = created_at;
