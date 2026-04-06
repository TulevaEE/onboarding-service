CREATE TABLE investment_tracking_difference_event (
    id bigserial NOT NULL,
    fund_code text NOT NULL,
    check_date date NOT NULL,
    check_type text NOT NULL,
    tracking_difference numeric(10, 6) NOT NULL,
    fund_return numeric(10, 6) NOT NULL,
    benchmark_return numeric(10, 6) NOT NULL,
    breach boolean NOT NULL DEFAULT false,
    consecutive_breach_days int NOT NULL DEFAULT 0,
    result jsonb NOT NULL DEFAULT '{}',
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT investment_td_event_pkey PRIMARY KEY (id),
    CONSTRAINT uq_td_event_fund_date_type UNIQUE (fund_code, check_date, check_type)
);

CREATE INDEX idx_td_event_fund_date ON investment_tracking_difference_event(fund_code, check_date);
CREATE INDEX idx_td_event_breach ON investment_tracking_difference_event(breach);
