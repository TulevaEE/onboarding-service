
CREATE TABLE IF NOT EXISTS swedbank_statement_fetch_job
(
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  tracking_id TEXT,
  job_status VARCHAR(255) NOT NULL,
  last_check_at TIMESTAMPTZ,
  raw_response TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
