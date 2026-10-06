CREATE TABLE investment_ownership_limit (
    id bigserial NOT NULL,
    effective_date date NOT NULL,
    fund_code text NOT NULL,
    soft_limit_percent numeric(10, 8) NOT NULL,
    hard_limit_percent numeric(10, 8) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT investment_ownership_limit_pkey PRIMARY KEY (id),
    CONSTRAINT uq_ownership_limit UNIQUE (effective_date, fund_code)
);

INSERT INTO investment_ownership_limit (effective_date, fund_code, soft_limit_percent, hard_limit_percent)
VALUES ('2026-09-30', 'TKF100', 20, 25);
