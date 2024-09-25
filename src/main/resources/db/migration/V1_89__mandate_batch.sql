CREATE TABLE mandate_batch (
  id BIGSERIAL PRIMARY KEY,
  status VARCHAR(255) NOT NULL,
  created_date TIMESTAMP NOT NULL DEFAULT NOW(),
  file BYTEA
);

ALTER TABLE mandate
  ADD COLUMN mandate_batch_id BIGINT;

ALTER TABLE mandate
  ADD CONSTRAINT fk_mandate_batch
    FOREIGN KEY (mandate_batch_id) REFERENCES mandate_batch(id)
    ON DELETE SET NULL;
