DELETE FROM investment_fund_position backfilled
WHERE backfilled.nav_date BETWEEN DATE '2026-01-20' AND DATE '2026-01-26'
  AND EXISTS (
    SELECT 1
    FROM investment_fund_position original
    WHERE original.fund_code = backfilled.fund_code
      AND original.nav_date = backfilled.nav_date
      AND original.account_type = backfilled.account_type
      AND original.account_id = backfilled.account_id
      AND original.created_at <= backfilled.created_at - INTERVAL '7' DAY
  );
