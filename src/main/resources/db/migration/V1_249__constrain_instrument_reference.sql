-- =============================================================================
-- Give the database the invariants that instrument metadata used to get from the compiler.
-- After the cutover an instrument change is an UPDATE typed into a console, so
-- a mistyped benchmark category, instrument type or asset class has to fail at
-- the moment of the change instead of silently re-pointing a live benchmark.
--
-- Every statement below is re-runnable: this script is applied by hand on
-- production first and then replayed by Flyway, so running it twice must leave
-- exactly the same state as running it once. Each ADD CONSTRAINT is therefore
-- preceded by DROP CONSTRAINT IF EXISTS (neither database has
-- ADD CONSTRAINT IF NOT EXISTS), the proxy re-keying is additive rather than a
-- recreate-and-rename, and its backfill only touches rows that have not been
-- backfilled yet, so a proxy someone has since re-pointed is never reset.
-- =============================================================================

ALTER TABLE instrument_reference
    DROP CONSTRAINT IF EXISTS instrument_reference_instrument_type_check;
ALTER TABLE instrument_reference
    ADD CONSTRAINT instrument_reference_instrument_type_check
        CHECK (instrument_type IS NULL OR instrument_type IN ('ETF', 'FUND'));

ALTER TABLE instrument_reference
    DROP CONSTRAINT IF EXISTS instrument_reference_asset_class_check;
ALTER TABLE instrument_reference
    ADD CONSTRAINT instrument_reference_asset_class_check
        CHECK (asset_class IS NULL OR asset_class IN ('equity', 'bond'));

-- =============================================================================
-- Re-key benchmark_category_proxy from derived storage-key strings to
-- references. The storage keys are now derived from the referenced instrument
-- (InstrumentReference.getXetraStorageKey() / getEuronextParisStorageKey()), so
-- re-listing a proxy ETF on another exchange no longer needs a manual string fix.
-- =============================================================================

ALTER TABLE benchmark_category_proxy ADD COLUMN IF NOT EXISTS etf_proxy_isin   varchar(12);
ALTER TABLE benchmark_category_proxy ADD COLUMN IF NOT EXISTS index_proxy_isin varchar(12);
ALTER TABLE benchmark_category_proxy ADD COLUMN IF NOT EXISTS index_series_key text;

-- EQUITY_DM/EQUITY_EM index positions track an MSCI index series, not an ETF.
-- BOND_EURO/BOND_GLOBAL have no separate index series, so index positions track
-- the same proxy ETF as exchange-traded positions do.
-- "etf_proxy_isin IS NULL" means "this row has not been re-keyed yet", so a
-- second run leaves an already re-keyed row — including one re-pointed to a
-- different proxy since — exactly as it found it.
UPDATE benchmark_category_proxy
    SET etf_proxy_isin = 'IE00B4L5Y983', index_proxy_isin = NULL, index_series_key = 'MSCI_WORLD'
    WHERE benchmark_category = 'EQUITY_DM' AND etf_proxy_isin IS NULL;
UPDATE benchmark_category_proxy
    SET etf_proxy_isin = 'IE00B4L5YC18', index_proxy_isin = NULL, index_series_key = 'MSCI_EM'
    WHERE benchmark_category = 'EQUITY_EM' AND etf_proxy_isin IS NULL;
UPDATE benchmark_category_proxy
    SET etf_proxy_isin = 'IE00B3DKXQ41', index_proxy_isin = 'IE00B3DKXQ41', index_series_key = NULL
    WHERE benchmark_category = 'BOND_EURO' AND etf_proxy_isin IS NULL;
UPDATE benchmark_category_proxy
    SET etf_proxy_isin = 'IE00BDBRDM35', index_proxy_isin = 'IE00BDBRDM35', index_series_key = NULL
    WHERE benchmark_category = 'BOND_GLOBAL' AND etf_proxy_isin IS NULL;

-- A category the backfill above does not know about keeps a NULL etf_proxy_isin
-- and fails here, rather than surviving as a proxy that points at nothing.
ALTER TABLE benchmark_category_proxy ALTER COLUMN etf_proxy_isin SET NOT NULL;

-- The legacy etf_proxy_storage_key / index_proxy_key columns are deliberately left in
-- place: the currently deployed release still selects them, so dropping them here would
-- break every running instance until the rolling deploy completes. V1_250 drops them in
-- a later release, once no deployed code reads them.

ALTER TABLE benchmark_category_proxy
    DROP CONSTRAINT IF EXISTS benchmark_category_proxy_etf_proxy_fkey;
ALTER TABLE benchmark_category_proxy
    ADD CONSTRAINT benchmark_category_proxy_etf_proxy_fkey
        FOREIGN KEY (etf_proxy_isin) REFERENCES instrument_reference (isin);

ALTER TABLE benchmark_category_proxy
    DROP CONSTRAINT IF EXISTS benchmark_category_proxy_index_proxy_fkey;
ALTER TABLE benchmark_category_proxy
    ADD CONSTRAINT benchmark_category_proxy_index_proxy_fkey
        FOREIGN KEY (index_proxy_isin) REFERENCES instrument_reference (isin);

ALTER TABLE benchmark_category_proxy
    DROP CONSTRAINT IF EXISTS benchmark_category_proxy_index_target_check;
ALTER TABLE benchmark_category_proxy
    ADD CONSTRAINT benchmark_category_proxy_index_target_check
        CHECK ((index_proxy_isin IS NOT NULL AND index_series_key IS NULL)
            OR (index_proxy_isin IS NULL AND index_series_key IS NOT NULL));

-- A benchmark category may only name a category that actually has a proxy.
-- NULL stays allowed: the proxy ETFs themselves ARE the benchmarks and
-- deliberately carry no category.
ALTER TABLE instrument_reference
    DROP CONSTRAINT IF EXISTS instrument_reference_benchmark_category_fkey;
ALTER TABLE instrument_reference
    ADD CONSTRAINT instrument_reference_benchmark_category_fkey
        FOREIGN KEY (benchmark_category) REFERENCES benchmark_category_proxy (benchmark_category);

-- =============================================================================
-- Append-only audit of every change to the instrument reference tables:
-- instrument_reference and benchmark_category_proxy alike. Re-pointing a proxy
-- silently changes what a live model portfolio is measured against, so it is
-- audited on exactly the same terms as an instrument edit. Written by a
-- PostgreSQL trigger (db/pg/V1_249_1) -- on H2 the table exists but stays empty.
-- record_key holds whatever identifies the row to a human: the isin for
-- instrument_reference, the benchmark_category for benchmark_category_proxy.
-- notified_at is stamped once InstrumentValidationJob has mailed the change out.
-- IF NOT EXISTS keeps a replay of this script from disturbing recorded history.
-- =============================================================================

CREATE TABLE IF NOT EXISTS reference_data_history (
    id          bigserial   NOT NULL,
    table_name  text        NOT NULL,
    record_key  text        NOT NULL,
    operation   varchar(6)  NOT NULL,
    old_values  jsonb,
    new_values  jsonb,
    changed_by  text        NOT NULL,
    changed_at  timestamptz NOT NULL,
    notified_at timestamptz,

    CONSTRAINT reference_data_history_pkey PRIMARY KEY (id),
    CONSTRAINT reference_data_history_operation_check
        CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE'))
);

CREATE INDEX IF NOT EXISTS reference_data_history_unnotified_index
    ON reference_data_history (notified_at, id);
