ALTER TABLE banking_message ADD COLUMN message_type text;
ALTER TABLE banking_message ADD COLUMN account_iban text;
ALTER TABLE banking_message ADD COLUMN statement_from date;
ALTER TABLE banking_message ADD COLUMN statement_to date;

CREATE INDEX idx_banking_message_statement_coverage
  ON banking_message (bank_type, account_iban, statement_from, statement_to);
