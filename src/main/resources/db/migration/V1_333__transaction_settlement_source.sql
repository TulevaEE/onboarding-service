ALTER TABLE transaction_settlement
    ADD COLUMN source text NOT NULL DEFAULT 'PENDING_REPORT_ABSENCE';

UPDATE transaction_settlement
SET source = 'HISTORICAL_IMPORT'
WHERE order_id IN (SELECT o.id
                   FROM investment_transaction_order o
                            JOIN investment_transaction_batch b ON b.id = o.batch_id
                   WHERE b.created_by = 'historical-import')
  AND NOT EXISTS (SELECT 1
                  FROM investment_transaction_audit_event e
                  WHERE e.order_id = transaction_settlement.order_id
                    AND e.event_type IN ('SETTLEMENT_DETECTED', 'SETTLEMENT_REDATED'));
