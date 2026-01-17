ALTER TABLE swedbank_message RENAME TO banking_message;

ALTER TABLE banking_message ADD COLUMN bank_type TEXT NOT NULL DEFAULT 'SWEDBANK';

CREATE INDEX idx_banking_message_bank_type ON banking_message(bank_type);
