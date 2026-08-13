-- How a fee rate row gets its rate:
--   FIXED -- annual_rate is the rate
--   TIER  -- the rate comes from investment_depot_fee_tier, by total AUM, at the fee month
--
-- Before this, "use the tier" was expressed by having no row at all, so a lapsed valid_to or a
-- deleted row silently switched a fund from a 0 rate to the tier rate. Using the tier is now a
-- row you add rather than a row you remove, and a missing row means no fee.
-- The three rules this column carries -- the value is FIXED or TIER, TIER is only meaningful for
-- DEPOT, and a TIER row leaves annual_rate at 0 -- are enforced in code and asserted over this
-- table in FeeRepositoriesIntegrationTest, not by CHECK constraints. A CHECK added here by ALTER
-- is not re-evaluated correctly by H2 once a second Spring context has opened the shared test
-- database: every insert that relies on the column default then fails with "Check constraint
-- invalid", which took the whole fee and NAV suite red while PostgreSQL was perfectly happy.
--
-- varchar, not text: H2 maps text to a CLOB, which cannot be compared inside a CHECK constraint.
--
-- Whichever way a rate is resolved, it must be entered VAT-inclusive. The accrual computed from it
-- is stored in investment_fee_accrual.daily_amount_gross and posted to the ledger as the amount
-- charged, with no VAT step anywhere after this point. The tier rates already satisfy this; a
-- hand-entered FIXED depot rate must too, or the column name stops being true.
ALTER TABLE investment_fee_rate
    ADD COLUMN rate_source varchar(10) DEFAULT 'FIXED' NOT NULL;

-- The depot fee starts being accrued at the actual tier rate on 2026-09-18. Until 2026-09-17 the
-- existing 0 rate stands, so September accrues nothing for days 1-17 and the September tier rate
-- from day 18 on. This only changes what we record as Tuleva's cost: investment_fee_policy still
-- says the depot fee is not charged to any fund, so it stays out of every fund's NAV and ledger.
UPDATE investment_fee_rate
SET valid_to = DATE '2026-09-17'
WHERE fee_type = 'DEPOT'
  AND rate_source = 'FIXED'
  AND fund_code IN ('TUK75', 'TUK00', 'TUV100', 'TKF100')
  AND valid_from <= DATE '2026-09-17'
  AND (valid_to IS NULL OR valid_to > DATE '2026-09-17');

INSERT INTO investment_fee_rate
    (fund_code, fee_type, annual_rate, rate_source, valid_from, created_by)
VALUES ('TUK75', 'DEPOT', 0, 'TIER', DATE '2026-09-18', 'MIGRATION'),
       ('TUK00', 'DEPOT', 0, 'TIER', DATE '2026-09-18', 'MIGRATION'),
       ('TUV100', 'DEPOT', 0, 'TIER', DATE '2026-09-18', 'MIGRATION'),
       ('TKF100', 'DEPOT', 0, 'TIER', DATE '2026-09-18', 'MIGRATION');
