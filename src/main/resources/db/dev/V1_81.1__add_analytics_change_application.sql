CREATE TABLE analytics.change_application
(
  personal_id      CHAR(11) PRIMARY KEY,
  current_fund     VARCHAR(255) NOT NULL,
  new_fund         VARCHAR(255) NOT NULL,
  first_name       VARCHAR(255),
  last_name        VARCHAR(255),
  share_amount     NUMERIC,
  share_percentage NUMERIC,
  reporting_date   TIMESTAMP    NOT NULL,
  date_created     TIMESTAMP    NOT NULL
);
