CREATE TABLE IF NOT EXISTS public.exchange_transactions (
  id               BIGSERIAL PRIMARY KEY,
  reporting_date   DATE      NOT NULL,
  security_from    text      NOT NULL,
  security_to      text      NOT NULL,
  fund_manager_from text,
  fund_manager_to   text,
  code            text      NOT NULL,
  first_name      text      NOT NULL,
  name            text      NOT NULL,
  percentage      NUMERIC   NOT NULL,
  unit_amount     NUMERIC   NOT NULL,
  date_created    TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_exchange_transactions_exists_check
  ON public.exchange_transactions
    (reporting_date, security_from, security_to, code, unit_amount, percentage);
