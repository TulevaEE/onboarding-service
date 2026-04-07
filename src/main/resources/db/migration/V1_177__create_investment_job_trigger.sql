CREATE TABLE investment_job_trigger (
    id bigserial NOT NULL,
    job_name text NOT NULL,
    status text NOT NULL DEFAULT 'PENDING',
    created_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    completed_at timestamptz,
    error_message text,
    CONSTRAINT investment_job_trigger_pkey PRIMARY KEY (id),
    CONSTRAINT investment_job_trigger_status_check CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_investment_job_trigger_status ON investment_job_trigger(status);
