DELETE FROM index_values
WHERE id NOT IN (SELECT MIN(id)
                 FROM index_values
                 GROUP BY key, date, value);

ALTER TABLE index_values ADD CONSTRAINT index_values_key_date_key UNIQUE (key, date);

