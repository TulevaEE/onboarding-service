CREATE TABLE investment_limit_check_event (
    id bigserial NOT NULL,
    fund_code text NOT NULL,
    check_date date NOT NULL,
    check_type text NOT NULL,
    breaches_found boolean NOT NULL DEFAULT false,
    result jsonb NOT NULL DEFAULT '{}',
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT investment_limit_check_event_pkey PRIMARY KEY (id)
);

CREATE INDEX idx_limit_check_event_fund_date ON investment_limit_check_event(fund_code, check_date);
CREATE INDEX idx_limit_check_event_breaches ON investment_limit_check_event(breaches_found);

ALTER TABLE investment_position_limit ADD COLUMN index_group text;

ALTER TABLE investment_position_limit DROP CONSTRAINT IF EXISTS investment_position_limit_isin_check;
ALTER TABLE investment_position_limit ALTER COLUMN isin DROP NOT NULL;

ALTER TABLE investment_fund_limit ADD COLUMN max_free_cash numeric(15, 2);
