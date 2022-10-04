ALTER TABLE scheduled_email
  ADD COLUMN mandate_id integer REFERENCES mandate;

CREATE INDEX scheduled_email_mandate_id_index
  ON scheduled_email (mandate_id);
