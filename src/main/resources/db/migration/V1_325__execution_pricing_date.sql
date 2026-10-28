ALTER TABLE investment_transaction_execution ADD COLUMN pricing_date date;

UPDATE investment_transaction_execution
SET pricing_date = nav_date
WHERE nav_date IS NOT NULL;

UPDATE investment_transaction_execution
SET pricing_date = CAST(execution_timestamp AT TIME ZONE 'Europe/Tallinn' AS date)
WHERE pricing_date IS NULL
  AND execution_timestamp IS NOT NULL
  AND order_id IN (SELECT id FROM investment_transaction_order WHERE instrument_type = 'ETF');

CREATE INDEX idx_txn_execution_pricing_date ON investment_transaction_execution(pricing_date);

DROP VIEW IF EXISTS v_transaction_registry;

CREATE VIEW v_transaction_registry AS
SELECT
    o.id AS order_id,
    o.order_uuid AS order_uuid,
    o.batch_id AS batch_id,
    o.fund_code AS fund_code,
    o.instrument_isin AS instrument_isin,
    o.transaction_type AS transaction_type,
    o.instrument_type AS instrument_type,
    o.order_amount AS order_amount,
    o.order_quantity AS order_quantity,
    o.order_venue AS order_venue,
    o.order_type AS order_type,
    o.order_status AS order_status,
    o.order_timestamp AS order_timestamp,
    o.expected_settlement_date AS expected_settlement_date,
    o.created_at AS order_created_at,
    e.id AS execution_id,
    e.broker_transaction_id AS broker_transaction_id,
    e.execution_timestamp AS execution_timestamp,
    e.executed_quantity AS executed_quantity,
    e.unit_price AS unit_price,
    e.total_consideration AS total_consideration,
    e.commission_amount AS commission_amount,
    e.settlement_fee_amount AS settlement_fee_amount,
    e.settlement_penalty AS settlement_penalty,
    e.net_settlement_amount AS net_settlement_amount,
    e.scheduled_settlement_date AS scheduled_settlement_date,
    e.pricing_date AS pricing_date,
    e.source AS execution_source,
    s.id AS settlement_id,
    s.settled_at AS settled_at,
    s.report_date AS settlement_report_date,
    CASE
        WHEN s.id IS NOT NULL OR o.order_status = 'SETTLED' THEN 'SETTLED'
        WHEN e.id IS NOT NULL OR o.order_status = 'EXECUTED' THEN 'AWAITING_SETTLEMENT'
        WHEN o.order_status = 'SENT' THEN 'AWAITING_EXECUTION'
        ELSE o.order_status
    END AS derived_status
FROM investment_transaction_order o
LEFT JOIN investment_transaction_execution e ON e.order_id = o.id
LEFT JOIN transaction_settlement s ON s.order_id = o.id
WHERE o.order_status NOT IN ('DRAFT', 'DISCARDED');
