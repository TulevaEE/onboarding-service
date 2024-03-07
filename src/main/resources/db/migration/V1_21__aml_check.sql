CREATE TABLE aml_check
(
  id           SERIAL PRIMARY KEY,
  user_id      BIGINT,
  type         TEXT      NOT NULL,
  success      BOOLEAN   NOT NULL,
  created_time TIMESTAMP NOT NULL,
  CONSTRAINT aml_check_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id)
);
