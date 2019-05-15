CREATE TABLE member_capital_event
(
  id SERIAL PRIMARY KEY NOT NULL,
  member_id integer NOT NULL REFERENCES member,
  type VARCHAR(255) NOT NULL,
  fiat_value float NOT NULL,
  ownership_unit_amount float NULL,
  timestamp TIMESTAMP NOT NULL DEFAULT NOW()
);

create index member_capital_event_member_id_index
  on member_capital_event (member_id);

CREATE TABLE organisation_capital_event
(
  id SERIAL PRIMARY KEY NOT NULL,
  type VARCHAR(255) NOT NULL,
  fiat_value float NOT NULL,
  timestamp TIMESTAMP NOT NULL DEFAULT NOW()
);
