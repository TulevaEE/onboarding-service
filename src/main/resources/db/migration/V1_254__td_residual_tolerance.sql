-- How much of a period's tracking difference may stay unexplained before the attribution is not
-- to be trusted. The residual is defined as the tracking difference minus the named components,
-- so it always balances - its size is the only real signal that the attribution failed.
--
-- Stated as an ANNUAL rate and scaled to the period by sqrt(days/365), so one number governs
-- monthly, quarterly and annual runs alike. 17.5 bps a year works out at ~5.0 bps over a month
-- and ~8.7 bps over a quarter.
--
-- Provisional: calibrate against the residuals observed in the attribution backfill. The scaling
-- law is itself falsifiable - if the observed quarterly residual is about sqrt(3) times the
-- monthly one it is noise and this band is the right shape; if it is about 3 times, it is a
-- systematic leak and a missing component must be added rather than the band widened.
INSERT INTO investment_parameter (effective_date, parameter_name, fund_code, numeric_value)
VALUES ('2026-08-31', 'TD_RESIDUAL_TOLERANCE_ANNUAL', NULL, 0.00175);
