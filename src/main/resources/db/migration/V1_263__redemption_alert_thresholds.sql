-- REDEMPTION_PAYOUT_WARNING_THRESHOLD is in EUR and tracks the SEB WITHDRAWAL_EUR account payment
-- limit, which moves by agreement with SEB rather than by deploy. REDEMPTION_LIQUIDITY_WARNING_
-- SHARE_OF_AUM is a fraction of the fund's latest AUM above which assets should be sold at today's
-- prices rather than after tomorrow's NAV calculation.
--
-- Both are effective from the day RedemptionAlertJob started applying them, so the seeded values
-- reproduce every alert already sent instead of leaving the job silent behind this deploy date.
INSERT INTO investment_parameter (effective_date, parameter_name, fund_code, numeric_value)
VALUES
    ('2026-05-19', 'REDEMPTION_PAYOUT_WARNING_THRESHOLD', 'TKF100', 40000),
    ('2026-05-19', 'REDEMPTION_LIQUIDITY_WARNING_SHARE_OF_AUM', 'TKF100', 0.01);
