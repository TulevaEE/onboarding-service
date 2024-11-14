ALTER TABLE email
  ADD COLUMN mandate_batch_id BIGINT;

ALTER TABLE email
  ADD CONSTRAINT fk_mandate_batch
    FOREIGN KEY (mandate_batch_id) REFERENCES mandate_batch(id)
      ON DELETE SET NULL;
