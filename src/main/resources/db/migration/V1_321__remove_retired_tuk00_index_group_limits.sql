DELETE FROM investment_position_limit
WHERE fund_code = 'TUK00'
  AND isin IS NULL
  AND index_group IS NOT NULL;

UPDATE investment_position_limit
SET index_group = NULL
WHERE fund_code = 'TUK00'
  AND isin IS NOT NULL
  AND index_group IS NOT NULL;
