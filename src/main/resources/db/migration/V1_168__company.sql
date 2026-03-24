CREATE TABLE company (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    registry_code text NOT NULL,
    name text NOT NULL,
    created_date timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_company_registry_code UNIQUE (registry_code)
);

CREATE INDEX idx_company_registry_code ON company(registry_code);

CREATE TABLE user_company (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES users(id),
    company_id uuid NOT NULL REFERENCES company(id),
    relationship_type text NOT NULL,
    created_date timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_company UNIQUE (user_id, company_id, relationship_type)
);

CREATE INDEX idx_user_company_user_id ON user_company(user_id);
CREATE INDEX idx_user_company_company_id ON user_company(company_id);
