ALTER TABLE investment_fund_position
    DROP CONSTRAINT uq_investment_fund_position;

ALTER TABLE investment_fund_position
    ADD CONSTRAINT uq_investment_fund_position
    UNIQUE (nav_date, fund_code, account_type, account_name);
