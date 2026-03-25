INSERT INTO company_party (id, party_code, party_type, company_id, relationship_type, created_date)
VALUES (gen_random_uuid(), '40404049996', 'PERSON',
        (SELECT id FROM company WHERE registry_code = '12345678'),
        'BOARD_MEMBER',
        now());