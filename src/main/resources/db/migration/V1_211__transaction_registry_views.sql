CREATE OR REPLACE VIEW v_transaction_registry AS
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
    e.actual_settlement_date AS actual_settlement_date,
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

CREATE OR REPLACE VIEW v_delayed_settlements AS
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
    e.actual_settlement_date AS actual_settlement_date
FROM investment_transaction_order o
LEFT JOIN investment_transaction_execution e ON e.order_id = o.id
LEFT JOIN transaction_settlement s ON s.order_id = o.id
WHERE (o.order_status = 'EXECUTED' OR e.id IS NOT NULL)
  AND s.id IS NULL
  AND o.expected_settlement_date < CURRENT_DATE;

CREATE OR REPLACE VIEW v_overdue_orders AS
SELECT
    o.id AS order_id,
    o.order_uuid AS order_uuid,
    o.batch_id AS batch_id,
    o.fund_code AS fund_code,
    o.instrument_isin AS instrument_isin,
    o.transaction_type AS transaction_type,
    o.order_amount AS order_amount,
    o.order_quantity AS order_quantity,
    o.order_status AS order_status,
    o.expected_settlement_date AS expected_settlement_date,
    o.created_at AS order_created_at
FROM investment_transaction_order o
LEFT JOIN investment_transaction_execution e ON e.order_id = o.id
WHERE o.order_status = 'SENT'
  AND e.id IS NULL
  AND o.expected_settlement_date < CURRENT_DATE;

CREATE OR REPLACE VIEW v_execution_audit_trail AS
SELECT
    a.id AS audit_event_id,
    a.order_id AS order_id,
    o.order_uuid AS order_uuid,
    o.fund_code AS fund_code,
    a.event_type AS event_type,
    a.actor AS actor,
    a.payload AS payload,
    a.created_at AS created_at
FROM investment_transaction_audit_event a
JOIN investment_transaction_order o ON o.id = a.order_id
WHERE a.order_id IS NOT NULL
ORDER BY a.created_at, a.id;

CREATE OR REPLACE VIEW v_unmatched_seb_entries AS
SELECT
    a.id AS audit_event_id,
    a.event_type AS event_type,
    a.actor AS actor,
    a.payload AS payload,
    a.created_at AS created_at
FROM investment_transaction_audit_event a
WHERE a.event_type = 'UNMATCHED_SEB_TRANSACTION';

CREATE OR REPLACE VIEW v_depositary_reconciliation AS
SELECT
    o.id AS order_id,
    o.order_uuid AS order_uuid,
    o.fund_code AS fund_code,
    o.instrument_isin AS instrument_isin,
    o.transaction_type AS transaction_type,
    o.order_quantity AS order_quantity,
    e.executed_quantity AS executed_quantity,
    o.order_amount AS order_amount,
    e.total_consideration AS total_consideration,
    e.unit_price AS unit_price,
    e.actual_settlement_date AS actual_settlement_date,
    CASE
        WHEN o.order_quantity IS NOT NULL
         AND e.executed_quantity IS NOT NULL
         AND ABS(o.order_quantity - e.executed_quantity) > 0.0001 THEN TRUE
        ELSE FALSE
    END AS quantity_mismatch
FROM investment_transaction_order o
JOIN investment_transaction_execution e ON e.order_id = o.id;
