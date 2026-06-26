CREATE TABLE company_representation_right (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    company_id uuid NOT NULL,
    entry_id bigint,
    representation_type text,
    representation_type_text text,
    content text,
    start_date date,
    end_date date,
    created_date timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_company_representation_right_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT uq_company_representation_right UNIQUE (company_id, entry_id)
);

CREATE INDEX idx_company_representation_right_company_id ON company_representation_right (company_id);
