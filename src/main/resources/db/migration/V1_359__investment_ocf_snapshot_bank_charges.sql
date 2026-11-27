ALTER TABLE investment_ocf_snapshot ADD COLUMN bank_charges_rate numeric(10, 8);
ALTER TABLE investment_ocf_snapshot ADD COLUMN bank_charges_window_start date;
ALTER TABLE investment_ocf_snapshot ADD COLUMN bank_fees numeric(19, 2);
ALTER TABLE investment_ocf_snapshot ADD COLUMN overdraft_interest numeric(19, 2);
