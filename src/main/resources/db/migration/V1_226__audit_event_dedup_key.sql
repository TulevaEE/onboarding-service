ALTER TABLE investment_transaction_audit_event ADD COLUMN dedup_key text;

CREATE INDEX idx_audit_event_type_dedup_key
    ON investment_transaction_audit_event (event_type, dedup_key);
