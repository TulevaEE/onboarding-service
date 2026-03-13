ALTER TABLE investment_limit_check_event
  ADD CONSTRAINT uq_limit_check_event_fund_date_type
  UNIQUE (fund_code, check_date, check_type);
