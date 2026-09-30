-- The same H2 limitation as V1_249_1, reached here by CHECKs written inside CREATE TABLE: once a
-- second Spring context opens the shared in-memory database, a CHECK that compares a string
-- column fails every INSERT with 23514 ("check constraint invalid") and an empty expression.
-- PostgreSQL is unaffected, so the constraints stay wherever the application runs and are
-- dropped for H2 only. V1_293InstrumentFeeAgreementsAndRatesTest migrates a fresh database to
-- 1.293 itself, where H2 still evaluates them, and asserts each one refuses what it should.
ALTER TABLE investment_instrument_fee
    DROP CONSTRAINT IF EXISTS chk_instrument_fee_agreement_rebate_kind;
ALTER TABLE investment_instrument_fee
    DROP CONSTRAINT IF EXISTS chk_instrument_fee_agreement_fixed_net_carries_the_whole_cost;
ALTER TABLE investment_instrument_fee_rate
    DROP CONSTRAINT IF EXISTS chk_instrument_fee_rate_basis;
ALTER TABLE investment_instrument_fee_rate
    DROP CONSTRAINT IF EXISTS chk_instrument_fee_rate_a_fallback_says_why;
