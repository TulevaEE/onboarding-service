CREATE SCHEMA analytics;

CREATE TABLE analytics.third_pillar
(
  id             SERIAL PRIMARY KEY,
  personal_id    CHAR(11)  NOT NULL,
  first_name     VARCHAR(255),
  last_name      VARCHAR(255),
  phone_no       VARCHAR(255),
  email          VARCHAR(255),
  country        VARCHAR(255),
  reporting_date TIMESTAMP NOT NULL,
  date_created   TIMESTAMP NOT NULL DEFAULT NOW()
);
