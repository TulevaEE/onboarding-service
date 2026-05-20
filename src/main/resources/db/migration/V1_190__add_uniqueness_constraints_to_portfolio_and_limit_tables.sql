-- Model portfolio allocation: one row per (date, fund, isin).
-- isin is nullable but in practice always populated for allocation rows.
-- SQL UNIQUE allows multiple NULLs, so NULL-isin rows (if any) are unaffected.
ALTER TABLE investment_model_portfolio_allocation
    ADD CONSTRAINT uq_model_portfolio_allocation UNIQUE (effective_date, fund_code, isin);

-- Position limit: one row per (date, fund, isin).
-- isin is nullable for index-group aggregate limits (e.g. "Euro Aggregate indeks").
-- SQL UNIQUE allows multiple NULLs, so aggregate limits are unaffected.
ALTER TABLE investment_position_limit
    ADD CONSTRAINT uq_position_limit UNIQUE (effective_date, fund_code, isin);

-- Provider limit: one row per (date, fund, provider).
-- provider is NOT NULL, so this is a straightforward uniqueness guarantee.
ALTER TABLE investment_provider_limit
    ADD CONSTRAINT uq_provider_limit UNIQUE (effective_date, fund_code, provider);

-- Fund limit: one row per (date, fund).
ALTER TABLE investment_fund_limit
    ADD CONSTRAINT uq_fund_limit UNIQUE (effective_date, fund_code);
