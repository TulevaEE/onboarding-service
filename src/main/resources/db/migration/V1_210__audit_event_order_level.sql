ALTER TABLE investment_transaction_audit_event ALTER COLUMN batch_id DROP NOT NULL;

ALTER TABLE investment_transaction_audit_event ADD COLUMN order_id bigint;

ALTER TABLE investment_transaction_audit_event
    ADD CONSTRAINT fk_audit_event_order
        FOREIGN KEY (order_id) REFERENCES investment_transaction_order(id);

CREATE INDEX idx_audit_event_order_id ON investment_transaction_audit_event(order_id);
