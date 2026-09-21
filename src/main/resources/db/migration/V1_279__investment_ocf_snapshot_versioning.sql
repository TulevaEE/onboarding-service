-- Versioning, publishing and an audit trail for investment_ocf_snapshot.
--
-- Altered in place rather than rebuilt: a rebuild would drop every existing id, reset created_at,
-- and leave the identity sequence behind the copied rows. The defaults on version and complete are
-- what let the previous release's MERGE (which sets neither) still insert if the code is rolled
-- back while the schema stays.

ALTER TABLE investment_ocf_snapshot
    DROP CONSTRAINT investment_ocf_snapshot_fund_month_key;

ALTER TABLE investment_ocf_snapshot ADD COLUMN version int NOT NULL DEFAULT 1;

ALTER TABLE investment_ocf_snapshot ADD COLUMN nav_date date;
ALTER TABLE investment_ocf_snapshot ADD COLUMN nav_calculation_id uuid;
ALTER TABLE investment_ocf_snapshot ADD COLUMN assets_under_management numeric(19, 2);

ALTER TABLE investment_ocf_snapshot ADD COLUMN management_fee_rate_id bigint;

ALTER TABLE investment_ocf_snapshot ADD COLUMN depot_charged_to_fund boolean;
ALTER TABLE investment_ocf_snapshot ADD COLUMN depot_tier_nav_date date;
ALTER TABLE investment_ocf_snapshot ADD COLUMN depot_tier_basis numeric(19, 2);

ALTER TABLE investment_ocf_snapshot ADD COLUMN txn_window_start date;
ALTER TABLE investment_ocf_snapshot ADD COLUMN txn_window_end date;
ALTER TABLE investment_ocf_snapshot ADD COLUMN txn_commissions numeric(19, 2);
ALTER TABLE investment_ocf_snapshot ADD COLUMN txn_average_aum numeric(19, 2);
ALTER TABLE investment_ocf_snapshot ADD COLUMN txn_nav_dates text;

-- Existing rows were computed against an empty investment_instrument_fee, so their underlying fund
-- cost really is zero rather than unknown. They are incomplete, and they say why.
ALTER TABLE investment_ocf_snapshot ADD COLUMN complete boolean NOT NULL DEFAULT false;
ALTER TABLE investment_ocf_snapshot ADD COLUMN checks text NOT NULL DEFAULT '{}';

ALTER TABLE investment_ocf_snapshot ADD COLUMN calculated_at timestamptz;
ALTER TABLE investment_ocf_snapshot ADD COLUMN published_at timestamptz;
ALTER TABLE investment_ocf_snapshot ADD COLUMN published_in text;

UPDATE investment_ocf_snapshot
SET calculated_at = created_at,
    checks = '{"migrated":"computed before investment_instrument_fee carried any rate"}'
WHERE calculated_at IS NULL;

ALTER TABLE investment_ocf_snapshot ALTER COLUMN calculated_at SET NOT NULL;
ALTER TABLE investment_ocf_snapshot ALTER COLUMN calculated_at SET DEFAULT now();

ALTER TABLE investment_ocf_snapshot
    ADD CONSTRAINT investment_ocf_snapshot_fund_month_version_key
        UNIQUE (fund_code, snapshot_month, version);

ALTER TABLE investment_ocf_snapshot
    ADD CONSTRAINT investment_ocf_snapshot_version_positive
        CHECK (version >= 1);

-- Symmetric on purpose: a published row must say where it went, and a row that names a destination
-- must carry the timestamp. Either half alone is an unfinished publish.
ALTER TABLE investment_ocf_snapshot
    ADD CONSTRAINT investment_ocf_snapshot_publication_fields_together
        CHECK ((published_at IS NULL) = (published_in IS NULL));

CREATE INDEX idx_ocf_snapshot_published
    ON investment_ocf_snapshot (fund_code, snapshot_month, published_at);
