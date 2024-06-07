CREATE TABLE analytics.tuk00
(
  personal_id             CHAR(11) PRIMARY KEY,
  first_name              VARCHAR(255),
  last_name               VARCHAR(255),
  email                   VARCHAR(255),
  language                CHAR(3),
  early_withdrawal_date   DATE,
  early_withdrawal_status CHAR(1)
);

CREATE TABLE analytics.tuk75
(
  personal_id             CHAR(11) PRIMARY KEY,
  first_name              VARCHAR(255),
  last_name               VARCHAR(255),
  email                   VARCHAR(255),
  language                CHAR(3),
  early_withdrawal_date   DATE,
  early_withdrawal_status CHAR(1)
)
