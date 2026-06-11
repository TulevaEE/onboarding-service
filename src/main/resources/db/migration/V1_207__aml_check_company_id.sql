ALTER TABLE aml_check ADD COLUMN company_id uuid;

ALTER TABLE aml_check
    ADD CONSTRAINT fk_aml_check_company FOREIGN KEY (company_id) REFERENCES company (id);

CREATE INDEX idx_aml_check_company_id ON aml_check (company_id);
