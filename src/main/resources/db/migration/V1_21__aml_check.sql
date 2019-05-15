CREATE TABLE aml_check
(
  id           SERIAL PRIMARY KEY,
  user_id      BIGINT REFERENCES users (id),
  type         TEXT      NOT NULL,
  success      BOOLEAN   NOT NULL,
  created_time TIMESTAMP NOT NULL
);
