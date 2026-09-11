-- Sisekord nr 4 p 11.7 (and p 3.5) escalate when the tracking difference exceeds 0,1% and
-- "püsib enam kui kolm (3) tööpäeva" - persists MORE THAN three working days. V1_202 seeded 3,
-- which escalates on the third day and so fires one day before the rule requires. The first day
-- that satisfies "more than three" is the fourth consecutive breach day.
INSERT INTO investment_parameter (parameter_name, effective_date, numeric_value)
VALUES ('ESCALATION_THRESHOLD_DAYS', '2026-08-28', 4);
