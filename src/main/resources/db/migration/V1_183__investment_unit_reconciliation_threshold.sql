CREATE TABLE investment_unit_reconciliation_threshold (
    id             bigserial      NOT NULL,
    fund_code      varchar(20)    NOT NULL,
    warning_units  numeric(20, 8) NOT NULL,
    fail_units     numeric(20, 8),
    created_at     timestamptz    NOT NULL DEFAULT now(),
    updated_at     timestamptz    NOT NULL DEFAULT now(),

    CONSTRAINT investment_unit_reconciliation_threshold_pkey PRIMARY KEY (id),
    CONSTRAINT investment_unit_reconciliation_threshold_fund_code_unique UNIQUE (fund_code)
);

INSERT INTO investment_unit_reconciliation_threshold (fund_code, warning_units, fail_units) VALUES
    ('TUK75',  0,    NULL),
    ('TUK00',  0,    NULL),
    ('TUV100', 0,    NULL),
    ('TKF100', 0.02, 0.5);
