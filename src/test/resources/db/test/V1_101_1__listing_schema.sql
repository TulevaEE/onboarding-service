-- TODO: move to src/main/resources/db/migration once finished

CREATE TABLE listing
(
  id             SERIAL PRIMARY KEY NOT NULL,
  member_id      BIGINT             NOT NULL REFERENCES member (id),
  type           VARCHAR(4)         NOT NULL,
  units          NUMERIC(14, 2)     NOT NULL,
  price_per_unit NUMERIC(14, 2)     NOT NULL,
  currency       VARCHAR(3)         NOT NULL,
  state          VARCHAR(20)        NOT NULL,
  iban           VARCHAR(34),
  language       VARCHAR(3),
  expiry_time    TIMESTAMP          NOT NULL,
  created_time   TIMESTAMP          NOT NULL DEFAULT NOW(),
  cancelled_time TIMESTAMP,
  completed_time TIMESTAMP
);

CREATE INDEX listings_member_id_index ON listing (member_id);
