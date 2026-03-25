ALTER TABLE ledger.party ALTER COLUMN party_type TYPE text;
UPDATE ledger.party SET party_type = 'PERSON' WHERE party_type = 'USER';
DROP TYPE ledger.party_type;
CREATE TYPE ledger.party_type AS ENUM ('PERSON', 'LEGAL_ENTITY');
ALTER TABLE ledger.party ALTER COLUMN party_type TYPE ledger.party_type USING party_type::ledger.party_type;
