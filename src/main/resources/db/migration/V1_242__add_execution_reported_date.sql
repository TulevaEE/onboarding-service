-- The "As of" date of the custodian report that first carried this execution.
--
-- The pending-transactions report and the positions report share that clock: a trade present in
-- the pending report as of D is present in the positions report as of D. It is therefore the only
-- date on which "is this trade already in the position report" can be asked.
--
-- created_at cannot answer it. SEB sends the file for as-of D on the next business day, so our
-- ingestion instant is systematically one business day later than the date the trade became
-- visible, and every trade would look unexplained twice: once in the report where the quantity
-- moved, once in the window where ingestion put it.

ALTER TABLE investment_transaction_execution ADD COLUMN reported_date date;

-- Rows written before the column existed. Exact values start at this migration; both consumers
-- window over the last few position reports, so the approximation ages out of scope within days.
--
-- Historical-import rows never came from a custodian report at all, so this walks the same
-- fallback chain the importer now uses for new rows: trade date, then the order's own timestamp,
-- then the settlement date. Falling straight through to created_at would date an old trade to
-- whenever the import happened to run — a recent date — and the traded-quantity window filters on
-- reported_date alone, with no settled-order exclusion, so those trades would land inside a recent
-- position-report window and show up as quantity nobody can explain. An execution can reach this
-- state: the importer creates one whenever quantity, price or consideration is present, and none
-- of those requires execution_timestamp.
UPDATE investment_transaction_execution e
SET reported_date = COALESCE(
        CAST(e.execution_timestamp AS date),
        CAST((SELECT o.order_timestamp
              FROM investment_transaction_order o
              WHERE o.id = e.order_id) AS date),
        e.scheduled_settlement_date,
        CAST(e.created_at AS date))
WHERE e.reported_date IS NULL
  AND e.source = 'HISTORICAL_IMPORT';

-- Custodian-sourced rows: the ingestion date, which trails the true as-of date by one business
-- day. Overstating the date is the safe direction — a trade looks reported later than it was,
-- never earlier, so a position is never assumed present before the custodian showed it.
UPDATE investment_transaction_execution
SET reported_date = CAST(created_at AS date)
WHERE reported_date IS NULL;

-- created_at is NOT NULL DEFAULT now(), so the backfill above cannot leave a hole. Enforcing it
-- means a future insert path that forgets the date fails at the boundary rather than writing a
-- row that silently disappears from every window that filters on it.
ALTER TABLE investment_transaction_execution ALTER COLUMN reported_date SET NOT NULL;

CREATE INDEX ix_investment_transaction_execution_reported_date
    ON investment_transaction_execution (reported_date);
