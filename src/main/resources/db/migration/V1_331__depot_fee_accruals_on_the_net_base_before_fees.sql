UPDATE investment_fee_accrual depot
SET base_value         = (SELECT management.base_value
                          FROM investment_fee_accrual management
                          WHERE management.fee_type = 'MANAGEMENT'
                            AND management.fund_code = depot.fund_code
                            AND management.accrual_date = depot.accrual_date),
    daily_amount_gross = ROUND((SELECT management.base_value
                                FROM investment_fee_accrual management
                                WHERE management.fee_type = 'MANAGEMENT'
                                  AND management.fund_code = depot.fund_code
                                  AND management.accrual_date = depot.accrual_date)
                               * depot.annual_rate / depot.days_in_year, 6)
WHERE depot.fee_type = 'DEPOT'
  AND EXISTS (SELECT 1
              FROM investment_fee_accrual management
              WHERE management.fee_type = 'MANAGEMENT'
                AND management.fund_code = depot.fund_code
                AND management.accrual_date = depot.accrual_date
                AND management.base_value <> depot.base_value)
  AND NOT EXISTS (SELECT 1
                  FROM investment_fee_policy policy
                  WHERE policy.fund_code = depot.fund_code
                    AND policy.fee_type = 'DEPOT'
                    AND policy.charged_to_fund
                    AND policy.valid_from <= depot.accrual_date
                    AND (policy.valid_to IS NULL OR policy.valid_to >= depot.accrual_date));
