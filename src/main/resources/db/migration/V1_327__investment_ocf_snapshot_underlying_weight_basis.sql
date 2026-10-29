ALTER TABLE investment_ocf_snapshot ADD COLUMN underlying_weight_basis text;
ALTER TABLE investment_ocf_snapshot ADD COLUMN invested_ratio_month_end numeric(19, 12);
ALTER TABLE investment_ocf_snapshot ADD COLUMN invested_ratio_month_average numeric(19, 12);

UPDATE investment_ocf_snapshot
SET underlying_weight_basis = 'MONTH_END'
WHERE nav_calculation_id IS NOT NULL
  AND assets_under_management > 0;

ALTER TABLE investment_ocf_snapshot
    ADD CONSTRAINT investment_ocf_snapshot_underlying_weight_basis_known
        CHECK (underlying_weight_basis IN ('MONTH_END', 'MONTH_AVERAGE'));
