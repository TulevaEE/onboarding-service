CREATE TABLE investment_position_calculation (
    id BIGSERIAL PRIMARY KEY,

    isin VARCHAR(255) NOT NULL,
    fund_code VARCHAR(255) NOT NULL,
    date DATE NOT NULL,

    quantity NUMERIC(20, 8) NOT NULL,
    eodhd_price NUMERIC(20, 8),
    yahoo_price NUMERIC(20, 8),
    used_price NUMERIC(20, 8),
    price_source VARCHAR(20),

    calculated_market_value NUMERIC(20, 2),

    validation_status VARCHAR(255) NOT NULL,
    price_discrepancy_percent NUMERIC(10, 4),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_inv_position_calc UNIQUE (isin, fund_code, date)
);

CREATE INDEX idx_inv_pos_calc_date ON investment_position_calculation(date);
CREATE INDEX idx_inv_pos_calc_fund_code ON investment_position_calculation(fund_code);
CREATE INDEX idx_inv_pos_calc_status ON investment_position_calculation(validation_status);
