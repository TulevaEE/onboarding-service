CREATE OR REPLACE VIEW v_settlement_delays AS
SELECT
    o.id AS order_id,
    o.order_uuid AS order_uuid,
    o.fund_code AS fund_code,
    o.instrument_isin AS instrument_isin,
    o.transaction_type AS transaction_type,
    o.order_status AS order_status,
    o.expected_settlement_date AS expected_settlement_date,
    s.report_date AS actual_settlement_date,
    s.settled_at AS settled_at,
    CASE
        WHEN o.expected_settlement_date IS NULL THEN NULL
        WHEN s.report_date <= o.expected_settlement_date THEN TRUE
        ELSE FALSE
    END AS settled_on_time
FROM investment_transaction_order o
JOIN transaction_settlement s ON s.order_id = o.id;
