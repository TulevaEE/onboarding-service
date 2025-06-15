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
  expiry_time    TIMESTAMP          NOT NULL,
  created_time   TIMESTAMP          NOT NULL DEFAULT NOW(),
  cancelled_time TIMESTAMP,
  completed_time TIMESTAMP
);

CREATE INDEX listings_member_id_index ON listing (member_id);

CREATE TABLE deal
(
  id                  SERIAL PRIMARY KEY NOT NULL,
  listing_id          BIGINT             NOT NULL REFERENCES listing (id),
  member_id           BIGINT             NOT NULL REFERENCES member (id),
  state               VARCHAR(20)        NOT NULL,
  created_time        TIMESTAMP          NOT NULL DEFAULT NOW(),
  buyer_confirmed_time  TIMESTAMP,
  seller_confirmed_time TIMESTAMP
);

CREATE INDEX deal_listing_id_index ON deal (listing_id);
CREATE INDEX deal_member_id_index ON deal (member_id);
