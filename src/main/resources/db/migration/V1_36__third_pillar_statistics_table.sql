CREATE TABLE third_pillar_statistics
(
  id SERIAL PRIMARY KEY NOT NULL,
  mandate_id integer NOT NULL REFERENCES mandate,
  single_payment NUMERIC(10, 2),
  recurring_payment NUMERIC(10, 2),
  time TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX third_pillar_statistics_mandate_id_index
  ON third_pillar_statistics (mandate_id);
