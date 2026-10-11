DELETE FROM investment_fund_position
WHERE nav_date BETWEEN DATE '2026-01-20' AND DATE '2026-01-26'
  AND created_at >= TIMESTAMP '2026-02-20 09:57:00'
  AND created_at < TIMESTAMP '2026-02-20 09:58:00';
