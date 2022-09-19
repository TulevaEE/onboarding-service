CREATE TABLE payment
(
  id           SERIAL PRIMARY KEY NOT NULL,
  user_id      BIGINT NOT NULL REFERENCES users (id),
  created_time TIMESTAMP NOT NULL
);
