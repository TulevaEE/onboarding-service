ALTER TABLE investment_instrument_fee
    ADD COLUMN invoiced_fee_rate numeric(10, 8) NOT NULL DEFAULT 0;

UPDATE investment_instrument_fee
SET invoiced_fee_rate = GREATEST(rebate_rate, 0),
    rebate_rate       = GREATEST(-rebate_rate, 0);

ALTER TABLE investment_instrument_fee
    ADD CONSTRAINT chk_instrument_fee_rebate_is_a_discount CHECK (rebate_rate >= 0);

ALTER TABLE investment_instrument_fee
    ADD CONSTRAINT chk_instrument_fee_invoiced_is_a_charge CHECK (invoiced_fee_rate >= 0);
