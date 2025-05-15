CREATE SCHEMA IF NOT EXISTS public;

CREATE TABLE IF NOT EXISTS public.exchange_transaction_snapshot
(
  id             BIGSERIAL PRIMARY KEY,
  snapshot_taken_at       TIMESTAMP WITH TIME ZONE NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  reporting_date          DATE                     NOT NULL,
  security_from           TEXT                     NOT NULL,
  security_to             TEXT                     NOT NULL,
  fund_manager_from       TEXT,
  fund_manager_to         TEXT,
  code                    TEXT                     NOT NULL,
  first_name              TEXT                     NOT NULL,
  name                    TEXT                     NOT NULL,
  percentage              NUMERIC                  NOT NULL,
  unit_amount             NUMERIC                  NOT NULL,
  source_date_created     TIMESTAMP                NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_exchange_snapshot_taken_at
  ON public.exchange_transaction_snapshot (snapshot_taken_at);

CREATE INDEX IF NOT EXISTS idx_exchange_snapshot_reporting_date
  ON public.exchange_transaction_snapshot (reporting_date);

CREATE INDEX IF NOT EXISTS idx_exchange_snapshot_taken_reporting
  ON public.exchange_transaction_snapshot (snapshot_taken_at, reporting_date);
