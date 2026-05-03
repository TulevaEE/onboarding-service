ALTER TABLE nav_report ADD COLUMN calculation_id uuid;
ALTER TABLE nav_report ADD COLUMN published_at timestamptz;

UPDATE nav_report SET calculation_id = gen_random_uuid(), published_at = created_at
WHERE calculation_id IS NULL;

ALTER TABLE nav_report ALTER COLUMN calculation_id SET NOT NULL;

ALTER TABLE nav_report DROP CONSTRAINT uq_nav_report_date_fund_type_name;

CREATE INDEX idx_nav_report_calc ON nav_report (fund_code, nav_date, calculation_id);
CREATE INDEX idx_nav_report_latest ON nav_report (fund_code, nav_date, id DESC);
