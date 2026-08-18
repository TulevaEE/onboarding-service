-- The "As of" date of the custodian report that first carried this execution: the same clock
-- fund_position.nav_date runs on. created_at cannot stand in for it, because SEB sends the file
-- for as-of D on the next business day.

ALTER TABLE investment_transaction_execution ADD COLUMN reported_date date;

-- Historical-import rows never came from a custodian report, so the chain stops at the settlement
-- date rather than falling through to created_at: a row carrying none of these three keeps a null
-- reported_date, exactly as the importer leaves it.
UPDATE investment_transaction_execution e
SET reported_date = COALESCE(
        CAST(e.execution_timestamp AS date),
        CAST((SELECT o.order_timestamp
              FROM investment_transaction_order o
              WHERE o.id = e.order_id) AS date),
        e.scheduled_settlement_date)
WHERE e.reported_date IS NULL
  AND e.source = 'HISTORICAL_IMPORT';

-- HISTORICAL_IMPORT is excluded deliberately, so the nulls the statement above left survive.
UPDATE investment_transaction_execution
SET reported_date = CAST(created_at AS date)
WHERE reported_date IS NULL
  AND source <> 'HISTORICAL_IMPORT';

-- reported_date stays nullable permanently: a historical-import row with no date has no honest
-- value to hold, and PendingOrderImpactService reads null as "unknown" rather than synthesizing a
-- position from it. A follow-up NOT NULL migration would be wrong.

CREATE INDEX ix_investment_transaction_execution_reported_date
    ON investment_transaction_execution (reported_date);
