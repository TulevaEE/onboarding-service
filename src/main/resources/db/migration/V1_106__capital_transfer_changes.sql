ALTER TABLE capital_transfer_contract DROP COLUMN unit_price;
ALTER TABLE capital_transfer_contract ADD COLUMN total_price NUMERIC(14, 2) NOT NULL;
