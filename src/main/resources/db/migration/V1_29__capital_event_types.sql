drop view aggregated_capital_event;

ALTER TABLE member_capital_event
  ALTER COLUMN fiat_value TYPE NUMERIC(12,5);

ALTER TABLE member_capital_event
  ALTER COLUMN ownership_unit_amount TYPE NUMERIC(12,5);

ALTER TABLE organisation_capital_event
  ALTER COLUMN fiat_value TYPE NUMERIC(12,5);

CREATE VIEW aggregated_capital_event AS (
  SELECT id, type, fiat_value, date,
         total_fiat_value, total_ownership_unit_amount,
         (total_fiat_value / total_ownership_unit_amount) as ownership_unit_price
  FROM
    (SELECT id, type, fiat_value, date,
            (SELECT SUM(fiat_value) FROM organisation_capital_event WHERE date <= oce.date) as total_fiat_value,
            (SELECT SUM(ownership_unit_amount) FROM member_capital_event
             WHERE accounting_date <= oce.date
            ) as total_ownership_unit_amount
     FROM organisation_capital_event as oce) as organisation_capital_aggregated
);
