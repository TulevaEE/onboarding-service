-- Keep the underlying fund cost on both rebate bases, and let a row name the methodology that
-- produced it.
--
-- CESR/10-674 p 8(e) allows netting a rebate off an underlying fund's published charge only where
-- the rebate is not already in the fund's profit and loss account. Ours is booked as fund income,
-- so whether the netting is allowed is an open compliance question. Storing both bases means the
-- answer, whenever it arrives, needs no recalculation and no backfill.

ALTER TABLE investment_ocf_snapshot ADD COLUMN underlying_fund_cost_gross numeric(10, 8);
ALTER TABLE investment_ocf_snapshot ADD COLUMN underlying_fund_cost_net numeric(10, 8);
ALTER TABLE investment_ocf_snapshot ADD COLUMN rebate_basis text NOT NULL DEFAULT 'NET';

-- Existing rows were computed against an empty investment_instrument_fee, so their underlying cost
-- is zero on either basis. Copying the column across is therefore accurate, not an approximation.
UPDATE investment_ocf_snapshot
SET underlying_fund_cost_gross = underlying_fund_cost,
    underlying_fund_cost_net = underlying_fund_cost
WHERE underlying_fund_cost_net IS NULL;

-- Left nullable on purpose. underlying_fund_cost stays the figure that entered the total, and these
-- two say what it would have been on either basis. A row written by the previous release (the
-- rollback window V1_275 kept open) knows the first and not the other two, and null says so rather
-- than a zero that would contradict the column beside it.

-- Without this a five-year-old row cannot say whether it was weighted against net assets or against
-- the securities sleeve, which denominator applied, or which rebate basis entered the total. Every
-- input is versioned; the method that consumed them was not.
ALTER TABLE investment_ocf_snapshot
    ADD COLUMN methodology text NOT NULL DEFAULT 'EX_ANTE_NET_ASSETS_V1';
