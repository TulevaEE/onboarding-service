ALTER TABLE investment_ocf_snapshot_detail ADD COLUMN instrument_fee_id bigint;

UPDATE investment_ocf_snapshot_detail
SET instrument_fee_id = (
    SELECT r.instrument_fee_id
    FROM investment_instrument_fee_rate r
    WHERE r.id = investment_ocf_snapshot_detail.instrument_fee_rate_id);

ALTER TABLE investment_ocf_snapshot_detail ALTER COLUMN instrument_fee_rate_id DROP NOT NULL;

ALTER TABLE investment_ocf_snapshot_detail
    ADD CONSTRAINT fk_ocf_snapshot_detail_agreement
        FOREIGN KEY (instrument_fee_id) REFERENCES investment_instrument_fee (id);

ALTER TABLE investment_ocf_snapshot_detail DROP CONSTRAINT uq_ocf_snapshot_detail_snapshot_isin;

ALTER TABLE investment_ocf_snapshot_detail
    ADD CONSTRAINT uq_ocf_snapshot_detail_snapshot_isin_rate
        UNIQUE (snapshot_id, isin, instrument_fee_id, rate_basis);

CREATE INDEX idx_ocf_snapshot_detail_agreement ON investment_ocf_snapshot_detail (instrument_fee_id);
