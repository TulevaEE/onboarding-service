-- Sisekord nr 4 p 11.7 requires escalation when the tracking difference exceeds 0,1% and
-- persists over a three-working-day streak. It sets no condition on the size of the streak's
-- net tracking difference, so the net-TD gate must never be stricter than the daily threshold
-- it escalates from: at 0.005 a three-day streak of 0,15% per day compounded to ~0,45% and
-- was silently dropped. The gate is kept, at the daily threshold, so it still filters a streak
-- whose days offset each other to nearly nothing.
INSERT INTO investment_parameter (parameter_name, effective_date, numeric_value)
VALUES ('ESCALATION_NET_TD_THRESHOLD', '2026-08-28', 0.001);
