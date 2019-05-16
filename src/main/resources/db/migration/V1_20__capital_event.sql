CREATE TABLE member_capital_event
(
  id SERIAL PRIMARY KEY NOT NULL,
  member_id integer NOT NULL REFERENCES member,
  type VARCHAR(255) NOT NULL,
  fiat_value float NOT NULL,
  ownership_unit_amount float NULL,
  accounting_date DATE NOT NULL DEFAULT NOW(),
  effective_date DATE NOT NULL DEFAULT NOW()
);

create index member_capital_event_member_id_index
  on member_capital_event (member_id);

CREATE TABLE organisation_capital_event
(
  id SERIAL PRIMARY KEY NOT NULL,
  type VARCHAR(255) NOT NULL,
  fiat_value float NOT NULL,
  date DATE NOT NULL DEFAULT NOW()
);

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
