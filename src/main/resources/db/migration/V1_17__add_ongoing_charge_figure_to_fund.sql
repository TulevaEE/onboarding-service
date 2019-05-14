ALTER TABLE fund ADD COLUMN ongoing_charges_figure NUMERIC(10, 8);
UPDATE fund SET ongoing_charges_figure = 0.0 where ongoing_charges_figure is null;
ALTER TABLE fund ALTER COLUMN ongoing_charges_figure SET NOT NULL;