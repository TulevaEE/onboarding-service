-- Sisekord nr 4 p 11.7 does not say which of the three computed comparisons its 0,1%
-- governs; that is tuleva issue #441, still open, and the review draft's answer is merged
-- into the draft rather than in force. So BENCHMARK_MODEL gets its own parameter now but
-- keeps the rule's 0,1%: the separation ships, the behaviour does not change, and raising
-- it once the Nõukogu approves is an INSERT with a later effective_date, not a deploy.
INSERT INTO investment_parameter (parameter_name, effective_date, numeric_value)
VALUES ('BENCHMARK_MODEL_BREACH_THRESHOLD', '2026-09-23', 0.001);
