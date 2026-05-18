CREATE TABLE investment_portfolio_cost_basis (
    id bigserial NOT NULL,
    fund_isin varchar(12) NOT NULL,
    instrument_isin varchar(12) NOT NULL,
    as_of_date date NOT NULL,
    quantity numeric(19,4) NOT NULL,
    avg_unit_cost numeric(19,8) NOT NULL,
    total_cost numeric(19,2) NOT NULL,
    delta_quantity numeric(19,4) NOT NULL DEFAULT 0,
    source text NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_investment_portfolio_cost_basis PRIMARY KEY (id),
    CONSTRAINT uk_investment_portfolio_cost_basis
        UNIQUE (fund_isin, instrument_isin, as_of_date)
);

CREATE INDEX ix_investment_portfolio_cost_basis_fund_date
    ON investment_portfolio_cost_basis(fund_isin, as_of_date);
CREATE INDEX ix_investment_portfolio_cost_basis_isin_date
    ON investment_portfolio_cost_basis(instrument_isin, as_of_date);

CREATE TABLE investment_portfolio_baseline (
    id bigserial NOT NULL,
    fund_isin varchar(12) NOT NULL,
    baseline_date date NOT NULL,
    loaded_at timestamptz NOT NULL DEFAULT now(),
    loaded_by text,
    CONSTRAINT pk_investment_portfolio_baseline PRIMARY KEY (id),
    CONSTRAINT uk_investment_portfolio_baseline_fund UNIQUE (fund_isin)
);

CREATE TABLE investment_portfolio_baseline_entry (
    id bigserial NOT NULL,
    baseline_id bigint NOT NULL,
    instrument_isin varchar(12) NOT NULL,
    quantity numeric(19,4) NOT NULL,
    avg_unit_cost numeric(19,8) NOT NULL,
    CONSTRAINT pk_investment_portfolio_baseline_entry PRIMARY KEY (id),
    CONSTRAINT fk_investment_portfolio_baseline_entry_baseline
        FOREIGN KEY (baseline_id) REFERENCES investment_portfolio_baseline(id) ON DELETE CASCADE,
    CONSTRAINT uk_investment_portfolio_baseline_entry
        UNIQUE (baseline_id, instrument_isin)
);

CREATE INDEX ix_investment_portfolio_baseline_entry_baseline
    ON investment_portfolio_baseline_entry(baseline_id);
