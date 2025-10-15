ALTER TABLE ledger.transaction
    ADD COLUMN external_reference UUID;

CREATE INDEX idx_ledger_transaction_external_reference
    ON ledger.transaction (external_reference);