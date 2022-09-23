CREATE TABLE payment
(
  id           SERIAL PRIMARY KEY NOT NULL,
  user_id      BIGINT NOT NULL REFERENCES users (id),
  internal_reference UUID NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  created_time TIMESTAMP NOT NULL
);
