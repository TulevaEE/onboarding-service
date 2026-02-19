ALTER TABLE investment_fund_position DROP CONSTRAINT IF EXISTS uq_investment_fund_position;
DROP INDEX IF EXISTS idx_inv_fund_pos_reporting_date;

ALTER TABLE investment_fund_position RENAME COLUMN reporting_date TO nav_date;

ALTER TABLE investment_fund_position ADD COLUMN report_date DATE;

ALTER TABLE investment_fund_position
    ADD CONSTRAINT uq_investment_fund_position UNIQUE (nav_date, fund_code, account_name);

CREATE INDEX idx_inv_fund_pos_nav_date ON investment_fund_position(nav_date);
