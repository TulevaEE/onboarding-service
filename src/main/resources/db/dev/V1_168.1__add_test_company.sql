INSERT INTO company (registry_code, name)
SELECT '12345678', 'Test OÜ'
WHERE NOT EXISTS (SELECT 1 FROM company WHERE registry_code = '12345678');

INSERT INTO user_company (user_id, company_id, relationship_type)
SELECT u.id, c.id, rt.type
FROM users u
JOIN company c ON c.registry_code = '12345678'
CROSS JOIN (VALUES ('BOARD_MEMBER'), ('SHAREHOLDER'), ('BENEFICIAL_OWNER')) AS rt(type)
WHERE u.personal_code IN ('38812022762', '39911223344')
  AND NOT EXISTS (
    SELECT 1 FROM user_company uc
    WHERE uc.user_id = u.id AND uc.company_id = c.id AND uc.relationship_type = rt.type
  );
