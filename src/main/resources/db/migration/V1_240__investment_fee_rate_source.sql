-- How a fee rate row gets its rate:
--   FIXED -- annual_rate is the rate
--   TIER  -- the rate comes from investment_depot_fee_tier, by total AUM, at the fee month
--
-- Before this, "use the tier" was expressed by having no row at all, so a lapsed valid_to or a
-- deleted row silently switched a fund from a 0 rate to the tier rate. Using the tier is now a
-- row you add rather than a row you remove, and a missing row means no fee.
-- varchar, not text: H2 maps text to a CLOB, which cannot be compared inside a CHECK constraint.
ALTER TABLE investment_fee_rate
    ADD COLUMN rate_source varchar(10) DEFAULT 'FIXED' NOT NULL;

ALTER TABLE investment_fee_rate
    ADD CONSTRAINT investment_fee_rate_rate_source_check
        CHECK (rate_source IN ('FIXED', 'TIER'));

-- Only the depot fee has an AUM tier table to read.
ALTER TABLE investment_fee_rate
    ADD CONSTRAINT investment_fee_rate_tier_only_for_depot_check
        CHECK (rate_source = 'FIXED' OR CAST(fee_type AS varchar(20)) = 'DEPOT');

-- A TIER row carries no rate of its own; keep the column at 0 so it cannot be misread as one.
ALTER TABLE investment_fee_rate
    ADD CONSTRAINT investment_fee_rate_tier_rate_unused_check
        CHECK (rate_source = 'FIXED' OR annual_rate = 0);

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
