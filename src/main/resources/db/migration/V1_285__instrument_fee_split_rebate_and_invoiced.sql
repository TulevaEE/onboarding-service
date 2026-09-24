-- rebate_rate was signed on the rate template's convention: a rebate negative, a separately
-- invoiced management fee positive. In one column the two cancel and neither is recoverable, so
-- the invoiced fee gets its own column and both are held to be non-negative.
--
-- Altered in place rather than rebuilt, like V1_281: a rebuild would drop every existing id,
-- reset created_at, and leave the identity sequence behind the copied rows, so the next rate
-- inserted without an id would collide with a row already there. The CHECKs compare numbers
-- only, which H2 keeps when they are added by ALTER TABLE (V1_281's version check is the same
-- shape); string comparisons are what it cannot evaluate, see db/h2/V1_249_1.
--
-- The split is by sign and lossless: net_ocf is untouched and still equals
-- published_ocf - rebate_rate + invoiced_fee_rate. Two statements, so the rebate is only
-- flipped after the invoiced fee has been read off it.
ALTER TABLE investment_instrument_fee
    ADD COLUMN invoiced_fee_rate numeric(10, 8) NOT NULL DEFAULT 0;

UPDATE investment_instrument_fee
SET invoiced_fee_rate = GREATEST(rebate_rate, 0);

UPDATE investment_instrument_fee
SET rebate_rate = GREATEST(-rebate_rate, 0);

ALTER TABLE investment_instrument_fee
    ADD CONSTRAINT chk_instrument_fee_rebate_is_a_discount CHECK (rebate_rate >= 0);

ALTER TABLE investment_instrument_fee
    ADD CONSTRAINT chk_instrument_fee_invoiced_is_a_charge CHECK (invoiced_fee_rate >= 0);
