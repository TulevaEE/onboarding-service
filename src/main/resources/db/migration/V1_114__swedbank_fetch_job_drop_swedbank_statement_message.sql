DROP TABLE swedbank_statement_fetch_job;

CREATE TABLE IF NOT EXISTS swedbank_message
(
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  tracking_id TEXT NOT NULL,
  request_id TEXT NOT NULL,
  raw_response TEXT NOT NULL,
  failed_at TIMESTAMPTZ,
  processed_at TIMESTAMPTZ,
  received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
