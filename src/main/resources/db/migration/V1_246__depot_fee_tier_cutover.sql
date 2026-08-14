-- The depot fee starts being accrued at the actual tier rate on 2026-09-18.
--
-- Source of the date: the depoopank agreement -- this is the date from which the depoopank's fee
-- is payable. It is a contractual date, not an implementation convenience, so it does not move to
-- suit the deploy. Confirm it against the agreement before merging rather than trusting this line.
--
-- The existing 0 rate rows close on 2026-09-17 and TIER rows open on 2026-09-18, so September
-- accrues nothing for days 1-17 and the September tier rate from day 18 on. This only changes what
-- we record as Tuleva's cost: investment_fee_policy still says the depot fee is not charged to any
-- fund, so it stays out of every fund's NAV and ledger.
--
-- READ THIS IF YOU ARE DEPLOYING ON OR AFTER 2026-09-18.
--
-- The cutover is only correct if this migration lands before the date it names. Accruals are
-- written forward-only: FeeCalculationService.calculateFeesForNav starts at
-- findLatestAccrualDate(fund) + 1 and never revisits a day it has already written. Deploy on, say,
-- 25.09 and the days 18.09-24.09 have already been accrued at the old 0 rate, while these rows
-- claim the tier rate covered them. The rate table would then describe a cost that
-- investment_fee_accrual does not contain, and nothing warns: the accrual table is the only record
-- of what Tuleva actually paid the depoopank, and those days would read as zero forever.
--
-- The admin fee backfill does not repair this on its own -- it calls the same forward-only
-- calculation, so it no-ops over days that already have rows. Recovering the gap means deleting
-- those accrual rows first and then re-running the backfill:
--
--   DELETE FROM investment_fee_accrual
--    WHERE fee_type = 'DEPOT' AND accrual_date >= DATE '2026-09-18';
--   -- then POST /admin/backfill-fees per fund, from 2026-09-18
--
-- That is safe only because the depot fee is not charged to any fund: charged_to_fund = false
-- means no ledger entry was posted for these accruals and no NAV depends on them. Do not copy this
-- recipe for a fee a fund is charged.
--
-- The cheaper path is to move both dates in this migration to the deploy date and accept that the
-- days before it are recorded as zero, if that is a smaller lie than the one above.
--
-- The same gap opens without the calendar being involved. A TIER row carries annual_rate = 0 and
-- names the tier table as the real source; the previously deployed image has no rate_source column
-- to read, so DepotFeeCalculator finds the row, takes annual_rate, and accrues zero -- it never
-- consults the tier. Any old task that runs the fee accrual after this migration lands, whether
-- through an ECS rollback or the few minutes of overlap in a rolling deploy, records zero depot
-- cost for the days it covers, and forward-only accrual means it never corrects itself.
--
-- Recovery is the same as above: delete those DEPOT accrual rows and re-run the backfill. And the
-- same caveat applies -- it is safe only while investment_fee_policy says no fund is charged the
-- depot fee, so no ledger entry or NAV depends on those rows. Once a fund is charged one, this
-- migration needs a different shape: give the TIER rows the tier's own rate as annual_rate so an
-- old image reading annual_rate lands on the right number instead of zero.

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
