-- The two numbers RedemptionAlertJob compares each working day's pending TKF100 redemptions
-- against. Both are properties of this one fund's payout plumbing rather than fund-wide risk
-- appetite, so both are scoped to TKF100.
--
-- REDEMPTION_PAYOUT_WARNING_THRESHOLD is in EUR and tracks the SEB WITHDRAWAL_EUR account payment
-- limit: above it somebody has to ask SEB to raise the limit before the batch pays out. That makes
-- it a bank-side operational ceiling which moves by agreement with SEB, not a property of our
-- code - which is exactly why it does not belong in a deploy. 40 000 is the value the job has
-- warned at since 2026-04-27; a redemption in August 2026 came in above it and the limit did have
-- to be raised, so the band is at least the right order of magnitude.
--
-- REDEMPTION_LIQUIDITY_WARNING_SHARE_OF_AUM is a fraction of the fund's latest AUM: above it the
-- pending withdrawals are large enough that assets should be sold at today's prices rather than
-- after tomorrow's NAV calculation. 1% is a trading-friction judgement, not a regulatory limit,
-- and nothing in this repo calibrates it. Provisional: revisit against the realised cost of the
-- redemptions that actually tripped it.
--
-- Effective from 2026-05-19, the day the unified job started applying both numbers, so the seeded
-- values reproduce every alert already sent instead of leaving the job silent behind its own
-- deploy date.
INSERT INTO investment_parameter (effective_date, parameter_name, fund_code, numeric_value)
VALUES
    ('2026-05-19', 'REDEMPTION_PAYOUT_WARNING_THRESHOLD', 'TKF100', 40000),
    ('2026-05-19', 'REDEMPTION_LIQUIDITY_WARNING_SHARE_OF_AUM', 'TKF100', 0.01);
