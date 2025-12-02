ALTER TABLE savings_fund_onboarding ADD COLUMN status varchar(20);

UPDATE savings_fund_onboarding SET status = 'COMPLETED' WHERE status IS NULL;

ALTER TABLE savings_fund_onboarding ALTER COLUMN status SET NOT NULL;
