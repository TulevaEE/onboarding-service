-- Drop old indexes
DROP INDEX IF EXISTS idx_inv_fund_pos_nav_date;
DROP INDEX IF EXISTS idx_inv_fund_pos_isin;
DROP INDEX IF EXISTS idx_inv_fund_pos_asset_type;

-- Drop old unique constraint
ALTER TABLE investment_fund_position DROP CONSTRAINT IF EXISTS uq_investment_fund_position;

-- Rename columns
ALTER TABLE investment_fund_position RENAME COLUMN report_date TO reporting_date;
ALTER TABLE investment_fund_position RENAME COLUMN asset_type TO account_type;
ALTER TABLE investment_fund_position RENAME COLUMN asset_name TO account_name;
ALTER TABLE investment_fund_position RENAME COLUMN isin TO account_id;
ALTER TABLE investment_fund_position RENAME COLUMN price TO market_price;
ALTER TABLE investment_fund_position RENAME COLUMN asset_currency TO currency;

-- Drop unused columns
ALTER TABLE investment_fund_position DROP COLUMN nav_date;
ALTER TABLE investment_fund_position DROP COLUMN portfolio;
ALTER TABLE investment_fund_position DROP COLUMN instrument_type;
ALTER TABLE investment_fund_position DROP COLUMN security_id;
ALTER TABLE investment_fund_position DROP COLUMN issuer_name;
ALTER TABLE investment_fund_position DROP COLUMN fund_currency;
ALTER TABLE investment_fund_position DROP COLUMN percentage_of_nav;

-- Add new unique constraint
ALTER TABLE investment_fund_position
    ADD CONSTRAINT uq_investment_fund_position UNIQUE (reporting_date, fund_code, account_name);

-- Add new indexes
CREATE INDEX idx_inv_fund_pos_reporting_date ON investment_fund_position(reporting_date);
CREATE INDEX idx_inv_fund_pos_account_type ON investment_fund_position(account_type);
