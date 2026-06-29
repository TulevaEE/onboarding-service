-- A SEB order can be filled and settled in several pieces, each a distinct broker (Our) ref under
-- the same order. Allow multiple executions per order: drop the 1:1 order_id uniqueness and make the
-- broker transaction id the natural per-piece key (it is SEB's globally-unique trade id, which the
-- code already assumes via findByBrokerTransactionId -> Optional). NULL broker refs are rejected in
-- application code, so multiple NULLs (allowed by standard SQL UNIQUE) are not a concern here.

-- Drop the FK first: H2 backs both the UNIQUE(order_id) constraint and the FK(order_id) with one
-- shared index, so dropping the unique constraint alone leaves that (still unique) index serving the
-- FK and uniqueness keeps being enforced. Dropping the FK releases the index, then dropping the
-- unique constraint removes it, and re-adding the FK creates a fresh non-unique index. PostgreSQL
-- uses independent indexes, so this sequence is correct there too.
ALTER TABLE investment_transaction_execution
    DROP CONSTRAINT fk_investment_transaction_execution_order;

ALTER TABLE investment_transaction_execution
    DROP CONSTRAINT uk_investment_transaction_execution_order;

ALTER TABLE investment_transaction_execution
    ADD CONSTRAINT fk_investment_transaction_execution_order
        FOREIGN KEY (order_id) REFERENCES investment_transaction_order(id);

DROP INDEX ix_investment_transaction_execution_broker_tx;

ALTER TABLE investment_transaction_execution
    ADD CONSTRAINT uk_investment_transaction_execution_broker_tx UNIQUE (broker_transaction_id);

-- Depositary reconciliation must compare the ordered quantity to the SUM of the order's executions,
-- otherwise every partial piece (executed_quantity < order_quantity) is flagged as a false mismatch.
CREATE OR REPLACE VIEW v_depositary_reconciliation AS
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
    MAX(e.actual_settlement_date) AS actual_settlement_date,
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
