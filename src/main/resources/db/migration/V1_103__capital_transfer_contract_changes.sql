ALTER TABLE capital_transfer_contract DROP COLUMN share_type;
ALTER TABLE capital_transfer_contract DROP COLUMN unit_count;
ALTER TABLE capital_transfer_contract ADD COLUMN units_of_member_bonus NUMERIC(14, 2) NOT NULL;
ALTER TABLE capital_transfer_contract ADD COLUMN unit_count NUMERIC(14, 2) NOT NULL;
