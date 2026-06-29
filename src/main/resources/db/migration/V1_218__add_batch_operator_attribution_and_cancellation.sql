ALTER TABLE investment_transaction_batch
    ADD COLUMN confirmed_by text;

ALTER TABLE investment_transaction_batch
    ADD COLUMN confirmed_at timestamptz;

ALTER TABLE investment_transaction_batch
    ADD COLUMN cancellation_reason text;

ALTER TABLE investment_transaction_batch
    ADD COLUMN cancelled_by text;

ALTER TABLE investment_transaction_batch
    ADD COLUMN cancelled_at timestamptz;
