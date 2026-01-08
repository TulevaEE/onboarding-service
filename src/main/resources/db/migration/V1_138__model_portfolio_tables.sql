CREATE TABLE investment_model_portfolio_allocation (
    id BIGSERIAL PRIMARY KEY,
    effective_date DATE NOT NULL,
    fund_code VARCHAR(255) NOT NULL,
    isin VARCHAR(255),
    ticker VARCHAR(255),
    weight NUMERIC(10, 8) NOT NULL,
    label VARCHAR(255),
    provider VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_model_portfolio_effective_date ON investment_model_portfolio_allocation(effective_date);
CREATE INDEX idx_model_portfolio_fund_code ON investment_model_portfolio_allocation(fund_code);
CREATE INDEX idx_model_portfolio_provider ON investment_model_portfolio_allocation(provider);

CREATE TABLE investment_position_limit (
    id BIGSERIAL PRIMARY KEY,
    effective_date DATE NOT NULL,
    fund_code VARCHAR(255) NOT NULL,
    isin VARCHAR(255) NOT NULL,
    ticker VARCHAR(255),
    label VARCHAR(255),
    provider VARCHAR(50),
    soft_limit_percent NUMERIC(10, 8) NOT NULL,
    hard_limit_percent NUMERIC(10, 8) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_position_limit_effective_date ON investment_position_limit(effective_date);
CREATE INDEX idx_position_limit_fund_code ON investment_position_limit(fund_code);
CREATE INDEX idx_position_limit_provider ON investment_position_limit(provider);

CREATE TABLE investment_fund_limit (
    id BIGSERIAL PRIMARY KEY,
    effective_date DATE NOT NULL,
    fund_code VARCHAR(255) NOT NULL,
    reserve_soft NUMERIC(15, 2),
    reserve_hard NUMERIC(15, 2),
    min_transaction NUMERIC(15, 2),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fund_limit_effective_date ON investment_fund_limit(effective_date);
CREATE INDEX idx_fund_limit_fund_code ON investment_fund_limit(fund_code);

CREATE TABLE investment_provider_limit (
    id BIGSERIAL PRIMARY KEY,
    effective_date DATE NOT NULL,
    fund_code VARCHAR(255) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    soft_limit_percent NUMERIC(10, 8) NOT NULL,
    hard_limit_percent NUMERIC(10, 8) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_provider_limit_effective_date ON investment_provider_limit(effective_date);
CREATE INDEX idx_provider_limit_fund_code ON investment_provider_limit(fund_code);
CREATE INDEX idx_provider_limit_provider ON investment_provider_limit(provider);
