ALTER TABLE investment_transaction_command ADD COLUMN cash_comment text;

ALTER TABLE investment_transaction_command
  ADD CONSTRAINT chk_transaction_command_cash_and_comment_together
  CHECK ((cash IS NULL AND cash_comment IS NULL) OR (cash IS NOT NULL AND cash_comment IS NOT NULL));
