CREATE TABLE comparison_fund_values (
  id SERIAL PRIMARY KEY,
  fund VARCHAR(255) NOT NULL,
  time TIMESTAMP NOT NULL,
  value NUMERIC(10, 2) NOT NULL
);

CREATE INDEX comparison_fund_values_by_fund_and_time ON comparison_fund_values (fund, time);