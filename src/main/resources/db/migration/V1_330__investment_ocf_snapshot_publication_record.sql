ALTER TABLE investment_ocf_snapshot ADD COLUMN published_valid_from date;
ALTER TABLE investment_ocf_snapshot ADD COLUMN published_ocf numeric(10, 8);
ALTER TABLE investment_ocf_snapshot ADD COLUMN published_management_fee_rate numeric(10, 8);
ALTER TABLE investment_ocf_snapshot ADD COLUMN published_management_fee_rate_id bigint;
ALTER TABLE investment_ocf_snapshot ADD COLUMN published_by text;
ALTER TABLE investment_ocf_snapshot ADD COLUMN publication_approved_by text;
ALTER TABLE investment_ocf_snapshot ADD COLUMN publication_exception_reason text;

CREATE INDEX idx_ocf_snapshot_published_valid_from
    ON investment_ocf_snapshot (fund_code, published_valid_from);
