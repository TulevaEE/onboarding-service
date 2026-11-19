INSERT INTO investment_portfolio_cost_basis
  (fund_isin, instrument_isin, as_of_date, quantity, avg_unit_cost, total_cost, source)
SELECT 'EE0000003283', account_id, nav_date, quantity, 0, 0, 'TEST_FIXTURE'
FROM investment_fund_position
WHERE fund_code = 'TKF100'
  AND nav_date = DATE '2026-02-10'
  AND account_type = 'SECURITY'
  AND account_id IS NOT NULL
  AND quantity IS NOT NULL;
