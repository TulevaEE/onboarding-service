-- Judge a FUND settlement against the date SEB itself first reported, not against our estimate.
--
-- expected_settlement_date is our own T+N calculation at order-send time. For ETFs that is
-- accurate. For funds it is not: the settlement chain runs through the underlying fund's own
-- dealing calendar, so a flat T+N is only the mode. Comparing against it produces both false
-- positives (our estimate was early) and misses (the fund settled late on a date our estimate
-- happened to land on).
--
-- SEB's pending report carries its own "Settlement date" per row, and we already store it on
-- the execution as scheduled_settlement_date. The first value SEB reports is the plan it has
-- committed to; a later restatement is the delay itself. So for funds the benchmark is the
-- earliest scheduled_settlement_date across the order's executions, falling back to our
-- estimate when the order never appeared in a pending report.
--
-- DROP first: CREATE OR REPLACE VIEW cannot insert columns ahead of existing ones, and
-- instrument_type / first_reported_settlement_date / benchmark_settlement_date belong next to
-- the dates they explain. Nothing else reads this view.
DROP VIEW IF EXISTS v_settlement_delays;

CREATE VIEW v_settlement_delays AS
WITH seb_plan AS (
    SELECT
        order_id,
        MIN(scheduled_settlement_date) AS first_reported_settlement_date
    FROM investment_transaction_execution
    WHERE scheduled_settlement_date IS NOT NULL
    GROUP BY order_id
),
benchmarked AS (
    SELECT
        o.id AS order_id,
        o.order_uuid AS order_uuid,
        o.fund_code AS fund_code,
        o.instrument_isin AS instrument_isin,
        o.instrument_type AS instrument_type,
        o.transaction_type AS transaction_type,
        o.order_status AS order_status,
        o.expected_settlement_date AS expected_settlement_date,
        p.first_reported_settlement_date AS first_reported_settlement_date,
        CASE
            WHEN o.instrument_type = 'FUND'
                THEN COALESCE(p.first_reported_settlement_date, o.expected_settlement_date)
            ELSE o.expected_settlement_date
        END AS benchmark_settlement_date,
        s.report_date AS actual_settlement_date,
        s.settled_at AS settled_at
    FROM investment_transaction_order o
    JOIN transaction_settlement s ON s.order_id = o.id
    LEFT JOIN seb_plan p ON p.order_id = o.id
)
SELECT
    b.order_id,
    b.order_uuid,
    b.fund_code,
    b.instrument_isin,
    b.instrument_type,
    b.transaction_type,
    b.order_status,
    b.expected_settlement_date,
    b.first_reported_settlement_date,
    b.benchmark_settlement_date,
    b.actual_settlement_date,
    b.settled_at,
    CASE
        WHEN b.benchmark_settlement_date IS NULL THEN NULL
        WHEN b.actual_settlement_date <= b.benchmark_settlement_date THEN TRUE
        ELSE FALSE
    END AS settled_on_time
FROM benchmarked b;
