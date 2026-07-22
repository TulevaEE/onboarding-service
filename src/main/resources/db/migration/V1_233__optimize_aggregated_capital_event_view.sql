DROP VIEW aggregated_capital_event;

CREATE VIEW aggregated_capital_event AS (
  SELECT id, type, fiat_value, date,
         total_fiat_value, total_ownership_unit_amount,
         (total_fiat_value / total_ownership_unit_amount) AS ownership_unit_price
  FROM
    (SELECT id, type, fiat_value, date,
            SUM(fiat_value) OVER (ORDER BY date) AS total_fiat_value,
            SUM(ownership_unit_amount) OVER (ORDER BY date) AS total_ownership_unit_amount,
            source
     FROM
       (SELECT id, type, fiat_value, date,
               CAST(NULL AS NUMERIC(12,5)) AS ownership_unit_amount,
               'organisation' AS source
        FROM organisation_capital_event
        UNION ALL
        SELECT CAST(NULL AS BIGINT), CAST(NULL AS VARCHAR(255)), CAST(NULL AS NUMERIC(12,5)),
               accounting_date, ownership_unit_amount, 'member'
        FROM member_capital_event) AS combined) AS organisation_capital_aggregated
  WHERE source = 'organisation'
);
