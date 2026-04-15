CREATE TABLE investment_health_check_event (
    id bigserial NOT NULL,
    fund_code text NOT NULL,
    check_date date NOT NULL,
    check_type text NOT NULL,
    issues_found boolean NOT NULL DEFAULT false,
    result jsonb NOT NULL DEFAULT '{}',
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT investment_health_check_event_pkey PRIMARY KEY (id),
    CONSTRAINT uq_health_check_event_fund_date_type UNIQUE (fund_code, check_date, check_type)
);

CREATE INDEX idx_health_check_event_fund_date ON investment_health_check_event(fund_code, check_date);
CREATE INDEX idx_health_check_event_issues ON investment_health_check_event(issues_found);
