CREATE TABLE investment_report (
    id bigserial NOT NULL,
    provider text NOT NULL,
    report_type text NOT NULL,
    report_date date NOT NULL,
    raw_data jsonb NOT NULL DEFAULT '[]'::jsonb,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT investment_report_pkey PRIMARY KEY (id),
    CONSTRAINT investment_report_unique UNIQUE (provider, report_type, report_date)
);

CREATE INDEX idx_investment_report_provider ON investment_report(provider);
CREATE INDEX idx_investment_report_report_type ON investment_report(report_type);
CREATE INDEX idx_investment_report_report_date ON investment_report(report_date);
