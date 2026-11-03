ALTER TABLE transaction_settlement
    ADD COLUMN source text NOT NULL DEFAULT 'PENDING_REPORT_ABSENCE';

UPDATE transaction_settlement
SET source = 'HISTORICAL_IMPORT'
WHERE order_id IN (SELECT o.id
                   FROM investment_transaction_order o
                            JOIN investment_transaction_batch b ON b.id = o.batch_id
                   WHERE b.created_by = 'historical-import');
