-- Since 18.09.2026 the fund rules deem a purchase order given by a deposit made by the unit
-- holder OR by a third party for their benefit, so deposits from someone other than the unit
-- holder are attributed instead of returned.
--
-- AML scoring needs to tell the two apart. Deriving it as
-- `remitter_id_code IS DISTINCT FROM party_code` is wrong whenever the bank does not send the
-- remitter id code (Montonio often omits it, foreign banks always do): a NULL then reads as a
-- third-party deposit even when the unit holder paid from their own account. Record the verdict
-- at verification time instead, where the remitter name is still available to fall back on.
--
-- NULL = undecided. Two ways to get there: a row created before this migration, and a row
-- verified since where the bank sent no usable remitter identity and the remitter name did not
-- match the unit holder. A name that does not match is not evidence of a third party (Wise
-- sends its own name), so neither case may be read as false.
ALTER TABLE saving_fund_payment
  ADD COLUMN third_party_deposit BOOLEAN;

COMMENT ON COLUMN saving_fund_payment.third_party_deposit IS
  'True when the remitter is someone other than the unit holder, false when it is the unit holder, NULL when we could not tell.';
