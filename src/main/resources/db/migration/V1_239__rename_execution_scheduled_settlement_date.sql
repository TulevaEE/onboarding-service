-- Rename investment_transaction_execution.actual_settlement_date -> scheduled_settlement_date.
-- The column is populated from the SEB pending report's SCHEDULED settlement date, not the date
-- the trade genuinely settled (that lives in transaction_settlement.report_date). Renaming makes the
-- name honest. Views that read this column are dropped first (engine-agnostic: neither H2 nor
-- PostgreSQL is trusted to auto-update a dependent view's output-column name across the rename),
-- then recreated with the renamed source AND renamed output alias.
--
-- v_settlement_delays (V1_219) intentionally aliases transaction_settlement.report_date AS
-- actual_settlement_date -- that IS the true actual date and is correctly named, so it is left
-- untouched. v_overdue_orders references only execution.id, not the renamed column, so it is not
-- dropped.

DROP VIEW IF EXISTS v_transaction_registry;
DROP VIEW IF EXISTS v_delayed_settlements;
DROP VIEW IF EXISTS v_depositary_reconciliation;

ALTER TABLE investment_transaction_execution
    RENAME COLUMN actual_settlement_date TO scheduled_settlement_date;

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
    e.nav_date AS nav_date,
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
LEFT JOIN transaction_settlement s ON s.order_id = o.id;

CREATE VIEW v_delayed_settlements AS
SELECT
    o.id AS order_id,
    o.order_uuid AS order_uuid,
    o.fund_code AS fund_code,
    o.instrument_isin AS instrument_isin,
    o.transaction_type AS transaction_type,
    o.order_status AS order_status,
    o.expected_settlement_date AS expected_settlement_date,
    e.id AS execution_id,
    e.broker_transaction_id AS broker_transaction_id,
    e.execution_timestamp AS execution_timestamp,
    e.executed_quantity AS executed_quantity,
    e.total_consideration AS total_consideration,
    e.scheduled_settlement_date AS scheduled_settlement_date
FROM investment_transaction_order o
LEFT JOIN investment_transaction_execution e ON e.order_id = o.id
LEFT JOIN transaction_settlement s ON s.order_id = o.id
WHERE (o.order_status = 'EXECUTED' OR e.id IS NOT NULL)
  AND s.id IS NULL
  AND o.expected_settlement_date < CURRENT_DATE;

CREATE VIEW v_depositary_reconciliation AS
SELECT
    o.id AS order_id,
    o.order_uuid AS order_uuid,
    o.fund_code AS fund_code,
    o.instrument_isin AS instrument_isin,
    o.transaction_type AS transaction_type,
    o.order_quantity AS order_quantity,
    SUM(e.executed_quantity) AS executed_quantity,
    o.order_amount AS order_amount,
    SUM(e.total_consideration) AS total_consideration,
    CASE
        WHEN SUM(e.executed_quantity) > 0
        THEN SUM(e.total_consideration) / SUM(e.executed_quantity)
    END AS unit_price,
    MAX(e.scheduled_settlement_date) AS scheduled_settlement_date,
    CASE
        WHEN o.order_quantity IS NOT NULL
         AND SUM(e.executed_quantity) IS NOT NULL
         AND ABS(o.order_quantity - SUM(e.executed_quantity)) > 0.0001 THEN TRUE
        ELSE FALSE
    END AS quantity_mismatch
FROM investment_transaction_order o
JOIN investment_transaction_execution e ON e.order_id = o.id
GROUP BY
    o.id, o.order_uuid, o.fund_code, o.instrument_isin, o.transaction_type,
    o.order_quantity, o.order_amount;
