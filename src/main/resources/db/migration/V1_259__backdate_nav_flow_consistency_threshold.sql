-- V1_258 seeded this at its deploy date, so every nav date behind it failed the import lookback.
INSERT INTO investment_parameter (effective_date, parameter_name, fund_code, numeric_value)
SELECT DATE '2026-01-01', 'NAV_FLOW_CONSISTENCY_THRESHOLD', NULL, 0.001
FROM (VALUES (1)) AS already_seeded(x)
WHERE NOT EXISTS (
    SELECT 1
    FROM investment_parameter
    WHERE parameter_name = 'NAV_FLOW_CONSISTENCY_THRESHOLD'
      AND fund_code IS NULL
      AND effective_date = DATE '2026-01-01'
);
