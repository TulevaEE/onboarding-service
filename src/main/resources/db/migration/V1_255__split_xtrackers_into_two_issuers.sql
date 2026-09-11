-- The provider limit is measured per issuer of the units, not per management company. Xtrackers is
-- where that distinction bites: IE00BJZ2DC62 is issued by Xtrackers (IE) plc and LU0476289540 by
-- the Luxembourg SICAV -- two legal issuers under one manager, DWS Investment S.A. Checked as one
-- group they are 19,00% + 2,00% = 21,00% against a 20% limit; as two they are 19,00% and 2,00%.
--
-- Only the version in force is relabelled. Earlier versions keep XTRACKERS: they record what was
-- approved and what the check said on those days, and Provider keeps the value so they still read.
--
-- The allocation rows are the load-bearing ones -- LimitCheckService builds the ISIN -> provider
-- map from them, so nothing else here changes a verdict on its own.

UPDATE investment_model_portfolio_allocation
SET provider = 'XTRACKERS_LU'
WHERE fund_code = 'TKF100'
  AND isin = 'LU0476289540'
  AND provider = 'XTRACKERS'
  AND effective_date = (SELECT MAX(a.effective_date)
                        FROM investment_model_portfolio_allocation a
                        WHERE a.fund_code = 'TKF100');

UPDATE investment_model_portfolio_allocation
SET provider = 'XTRACKERS_IE'
WHERE fund_code = 'TKF100'
  AND provider = 'XTRACKERS'
  AND effective_date = (SELECT MAX(a.effective_date)
                        FROM investment_model_portfolio_allocation a
                        WHERE a.fund_code = 'TKF100');

-- Position limits carry the same label for reading, not for checking: PositionLimitChecker works
-- per ISIN. Left behind, the column would disagree with the allocation it describes.

UPDATE investment_position_limit
SET provider = 'XTRACKERS_LU'
WHERE fund_code = 'TKF100'
  AND isin = 'LU0476289540'
  AND provider = 'XTRACKERS'
  AND effective_date = (SELECT MAX(l.effective_date)
                        FROM investment_position_limit l
                        WHERE l.fund_code = 'TKF100');

UPDATE investment_position_limit
SET provider = 'XTRACKERS_IE'
WHERE fund_code = 'TKF100'
  AND provider = 'XTRACKERS'
  AND effective_date = (SELECT MAX(l.effective_date)
                        FROM investment_position_limit l
                        WHERE l.fund_code = 'TKF100');

-- The limits follow: the existing row becomes the Irish issuer's, and the Luxembourg issuer gets
-- one of its own with the same soft and hard percentages. TKF100 goes from six rows to seven.
-- An issuer with no limit row is dropped from the check without a word, so the two must move
-- together -- which is why they are in one migration rather than two hand-run statements.

UPDATE investment_provider_limit
SET provider = 'XTRACKERS_IE'
WHERE fund_code = 'TKF100'
  AND provider = 'XTRACKERS'
  AND effective_date = (SELECT MAX(l.effective_date)
                        FROM investment_provider_limit l
                        WHERE l.fund_code = 'TKF100');

INSERT INTO investment_provider_limit
    (effective_date, fund_code, provider, soft_limit_percent, hard_limit_percent)
SELECT effective_date, fund_code, 'XTRACKERS_LU', soft_limit_percent, hard_limit_percent
FROM investment_provider_limit
WHERE fund_code = 'TKF100'
  AND provider = 'XTRACKERS_IE'
  AND effective_date = (SELECT MAX(l.effective_date)
                        FROM investment_provider_limit l
                        WHERE l.fund_code = 'TKF100')
  AND NOT EXISTS (SELECT 1
                  FROM investment_provider_limit x
                  WHERE x.fund_code = 'TKF100'
                    AND x.provider = 'XTRACKERS_LU'
                    AND x.effective_date = (SELECT MAX(l.effective_date)
                                            FROM investment_provider_limit l
                                            WHERE l.fund_code = 'TKF100'));
