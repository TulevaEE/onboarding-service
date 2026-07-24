-- Per-instrument settlement terms for cut-off-aware expected settlement dates.
--
-- Model: acceptance date = the submission date rolled to the next-or-same business day when
-- submitted at or before the cut-off (compared in the cut-off zone; exactly the cut-off instant
-- counts as before), otherwise the next business day. Business days follow the PROVIDER's domicile
-- calendar (CCF is Irish-domiciled but settles via Allfunds on the France calendar). Expected
-- settlement = acceptance + settlement_days_from_acceptance business days on that same calendar.
--
-- All three columns are nullable and act as one unit: if any is NULL the instrument keeps the flat
-- T+2 (ETF) / T+5 (FUND) path. This is the rollout safety net.
--
-- CCF (IE0009FT4LX4) settles T+3 from acceptance with a 09:30 Europe/Tallinn cut-off, matching
-- Allfunds reality: submitted at or before 09:30 -> T+3, after -> T+4.

ALTER TABLE instrument_reference ADD COLUMN settlement_cutoff_time time;
ALTER TABLE instrument_reference ADD COLUMN settlement_cutoff_zone text;
ALTER TABLE instrument_reference ADD COLUMN settlement_days_from_acceptance integer;

UPDATE instrument_reference
SET settlement_cutoff_time = TIME '09:30:00',
    settlement_cutoff_zone = 'Europe/Tallinn',
    settlement_days_from_acceptance = 3
WHERE isin = 'IE0009FT4LX4';
