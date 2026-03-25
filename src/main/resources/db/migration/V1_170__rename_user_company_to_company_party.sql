CREATE TABLE company_party (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    party_code text NOT NULL,
    party_type text NOT NULL,
    company_id uuid NOT NULL REFERENCES company(id),
    relationship_type text NOT NULL,
    created_date timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_company_party UNIQUE (party_code, party_type, company_id, relationship_type)
);

INSERT INTO company_party (id, party_code, party_type, company_id, relationship_type, created_date)
SELECT uc.id, u.personal_code, 'PERSON', uc.company_id, uc.relationship_type, uc.created_date
FROM user_company uc
JOIN users u ON u.id = uc.user_id;

DROP TABLE user_company;

CREATE INDEX idx_company_party_party ON company_party(party_code, party_type);
CREATE INDEX idx_company_party_company_id ON company_party(company_id);
