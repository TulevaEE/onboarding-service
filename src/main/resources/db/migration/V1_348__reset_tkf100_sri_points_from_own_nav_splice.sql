DELETE FROM investment_risk_indicator_point
WHERE indicator_type = 'SRI'
  AND fund_code = 'TKF100'
  AND as_of_date >= DATE '2026-03-01';
