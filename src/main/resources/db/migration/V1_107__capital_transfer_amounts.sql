ALTER TABLE capital_transfer_contract DROP COLUMN total_price;
ALTER TABLE capital_transfer_contract DROP COLUMN unit_count;
ALTER TABLE capital_transfer_contract DROP COLUMN units_of_member_bonus;
ALTER TABLE capital_transfer_contract ADD COLUMN transfer_amounts JSONB NOT NULL;
