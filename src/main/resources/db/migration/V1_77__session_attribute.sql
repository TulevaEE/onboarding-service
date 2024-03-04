CREATE TABLE IF NOT EXISTS session_attribute (
  id              BIGSERIAL PRIMARY KEY,
  user_id         BIGINT REFERENCES users (id),
  attribute_name  TEXT NOT NULL,
  attribute_value BYTEA NOT NULL,
  created_date    TIMESTAMP NOT NULL,
  CONSTRAINT session_attribute_uniq UNIQUE (user_id, attribute_name)
);
