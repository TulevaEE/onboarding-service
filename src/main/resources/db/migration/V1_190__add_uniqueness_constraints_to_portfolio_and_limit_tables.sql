-- Dedup before adding constraints: keep the highest-id row per uniqueness group, which
-- represents the most recently inserted version. Without dedup the ALTER TABLE would fail
-- on any environment that already accumulated duplicates.
--
-- NULL-isin rows are not deduped: SQL UNIQUE treats multiple NULLs as distinct, so they
-- don't violate the constraint and we don't want to discard index-group aggregate limits.

DELETE FROM investment_model_portfolio_allocation
WHERE isin IS NOT NULL
  AND id NOT IN (
    SELECT max_id FROM (
      SELECT MAX(id) AS max_id
      FROM investment_model_portfolio_allocation
      WHERE isin IS NOT NULL
      GROUP BY effective_date, fund_code, isin
    ) keepers
  );

DELETE FROM investment_position_limit
WHERE isin IS NOT NULL
  AND id NOT IN (
    SELECT max_id FROM (
      SELECT MAX(id) AS max_id
      FROM investment_position_limit
      WHERE isin IS NOT NULL
      GROUP BY effective_date, fund_code, isin
    ) keepers
  );

DELETE FROM investment_provider_limit
WHERE id NOT IN (
  SELECT max_id FROM (
    SELECT MAX(id) AS max_id
    FROM investment_provider_limit
    GROUP BY effective_date, fund_code, provider
  ) keepers
);

DELETE FROM investment_fund_limit
WHERE id NOT IN (
  SELECT max_id FROM (
    SELECT MAX(id) AS max_id
    FROM investment_fund_limit
    GROUP BY effective_date, fund_code
  ) keepers
);

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
