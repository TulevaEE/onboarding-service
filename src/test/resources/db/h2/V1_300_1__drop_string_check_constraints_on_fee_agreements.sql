ALTER TABLE investment_instrument_fee
    DROP CONSTRAINT IF EXISTS chk_instrument_fee_agreement_rebate_kind;
ALTER TABLE investment_instrument_fee
    DROP CONSTRAINT IF EXISTS chk_instrument_fee_agreement_fixed_net_carries_the_whole_cost;
ALTER TABLE investment_instrument_fee_rate
    DROP CONSTRAINT IF EXISTS chk_instrument_fee_rate_basis;
ALTER TABLE investment_instrument_fee_rate
    DROP CONSTRAINT IF EXISTS chk_instrument_fee_rate_a_fallback_says_why;
