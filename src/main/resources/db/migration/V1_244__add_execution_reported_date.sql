ALTER TABLE investment_transaction_execution ADD COLUMN reported_date date;

UPDATE investment_transaction_execution e
SET reported_date = COALESCE(
        CAST(e.execution_timestamp AS date),
        CAST((SELECT o.order_timestamp
              FROM investment_transaction_order o
              WHERE o.id = e.order_id) AS date),
        e.scheduled_settlement_date)
WHERE e.reported_date IS NULL
  AND e.source = 'HISTORICAL_IMPORT';

UPDATE investment_transaction_execution
SET reported_date = CAST(created_at AS date)
WHERE reported_date IS NULL
  AND source <> 'HISTORICAL_IMPORT';

CREATE INDEX ix_investment_transaction_execution_reported_date
    ON investment_transaction_execution (reported_date);
