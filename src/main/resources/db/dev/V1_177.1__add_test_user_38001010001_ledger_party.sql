INSERT INTO ledger.party (id, party_type, owner_id, details)
SELECT gen_random_uuid(), CAST('PERSON' AS ledger.party_type), '38001010001', '{}'
WHERE NOT EXISTS (
  SELECT 1 FROM ledger.party
  WHERE owner_id = '38001010001'
    AND party_type = CAST('PERSON' AS ledger.party_type)
);
