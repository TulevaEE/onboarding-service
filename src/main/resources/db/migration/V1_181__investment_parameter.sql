CREATE TABLE investment_parameter (
    id              bigserial       NOT NULL,
    effective_date  date            NOT NULL,
    parameter_name  varchar(64)     NOT NULL,
    fund_code       varchar(255),
    numeric_value   numeric(19, 10) NOT NULL,
    created_at      timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT investment_parameter_pkey PRIMARY KEY (id)
);

CREATE INDEX idx_investment_parameter_lookup
    ON investment_parameter (parameter_name, fund_code, effective_date);
