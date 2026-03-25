INSERT INTO ledger.party (id, party_type, owner_id, details)
SELECT gen_random_uuid(), CAST('USER' AS ledger.party_type), users.personal_code, '{}'
FROM users
WHERE users.personal_code IN ('38812022762', '39911223344')
  AND NOT EXISTS (
    SELECT 1 FROM ledger.party WHERE party.owner_id = users.personal_code
  );
