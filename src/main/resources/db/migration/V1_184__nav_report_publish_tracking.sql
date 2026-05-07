ALTER TABLE nav_report ADD COLUMN calculation_id uuid;
ALTER TABLE nav_report ADD COLUMN published_at timestamptz;

CREATE TABLE nav_report_calc_ids (fund_code text NOT NULL, nav_date date NOT NULL, calc_id uuid NOT NULL);
INSERT INTO nav_report_calc_ids (fund_code, nav_date, calc_id)
SELECT fund_code, nav_date, gen_random_uuid()
FROM nav_report
GROUP BY fund_code, nav_date;

UPDATE nav_report r SET
  calculation_id = (SELECT calc_id FROM nav_report_calc_ids
                    WHERE fund_code = r.fund_code AND nav_date = r.nav_date),
  published_at = r.created_at;

DROP TABLE nav_report_calc_ids;

ALTER TABLE nav_report ALTER COLUMN calculation_id SET NOT NULL;

ALTER TABLE nav_report DROP CONSTRAINT uq_nav_report_date_fund_type_name;

CREATE INDEX idx_nav_report_calc ON nav_report (fund_code, nav_date, calculation_id);
CREATE INDEX idx_nav_report_latest ON nav_report (fund_code, nav_date, id DESC);
