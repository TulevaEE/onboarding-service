CREATE TABLE investment_fund_position (
    id BIGSERIAL PRIMARY KEY,

    report_date DATE NOT NULL,
    nav_date DATE NOT NULL,

    portfolio VARCHAR(100) NOT NULL,
    fund_code VARCHAR(20) NOT NULL,

    asset_type VARCHAR(50) NOT NULL,
    instrument_type VARCHAR(100),
    isin VARCHAR(50),
    security_id VARCHAR(100),
    asset_name VARCHAR(500) NOT NULL,
    issuer_name VARCHAR(500),

    quantity NUMERIC(20, 8),
    fund_currency VARCHAR(3),
    asset_currency VARCHAR(3),
    price NUMERIC(20, 8),
    market_value NUMERIC(20, 2),
    percentage_of_nav NUMERIC(10, 6),

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_investment_fund_position UNIQUE (nav_date, fund_code, asset_name)
);

CREATE INDEX idx_inv_fund_pos_nav_date ON investment_fund_position(nav_date);
CREATE INDEX idx_inv_fund_pos_fund_code ON investment_fund_position(fund_code);
CREATE INDEX idx_inv_fund_pos_isin ON investment_fund_position(isin);
CREATE INDEX idx_inv_fund_pos_asset_type ON investment_fund_position(asset_type);
