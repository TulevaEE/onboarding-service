-- =============================================================================
-- Give the database the invariants that instrument metadata used to get from the compiler.
-- After the cutover an instrument change is an UPDATE typed into a console, so
-- a mistyped benchmark category, instrument type or asset class has to fail at
-- the moment of the change instead of silently re-pointing a live benchmark.
-- =============================================================================

ALTER TABLE instrument_reference
    ADD CONSTRAINT instrument_reference_instrument_type_check
        CHECK (instrument_type IS NULL OR instrument_type IN ('ETF', 'FUND'));

ALTER TABLE instrument_reference
    ADD CONSTRAINT instrument_reference_asset_class_check
        CHECK (asset_class IS NULL OR asset_class IN ('equity', 'bond'));

-- =============================================================================
-- Re-key benchmark_category_proxy from derived storage-key strings to
-- references. The storage keys are now derived from the referenced instrument
-- (InstrumentReference.getXetraStorageKey() / getEuronextParisStorageKey()), so
-- re-listing a proxy ETF on another exchange no longer needs a manual string fix.
-- =============================================================================

CREATE TABLE benchmark_category_proxy_new (
    id                 bigserial   NOT NULL,
    benchmark_category varchar(20) NOT NULL,
    etf_proxy_isin     varchar(12) NOT NULL,
    index_proxy_isin   varchar(12),
    index_series_key   text,

    CONSTRAINT benchmark_category_proxy_new_pkey PRIMARY KEY (id),
    CONSTRAINT benchmark_category_proxy_new_category_uq UNIQUE (benchmark_category),
    CONSTRAINT benchmark_category_proxy_new_etf_proxy_fkey
        FOREIGN KEY (etf_proxy_isin) REFERENCES instrument_reference (isin),
    CONSTRAINT benchmark_category_proxy_new_index_proxy_fkey
        FOREIGN KEY (index_proxy_isin) REFERENCES instrument_reference (isin),
    CONSTRAINT benchmark_category_proxy_new_index_target_check
        CHECK ((index_proxy_isin IS NOT NULL AND index_series_key IS NULL)
            OR (index_proxy_isin IS NULL AND index_series_key IS NOT NULL))
);

-- EQUITY_DM/EQUITY_EM index positions track an MSCI index series, not an ETF.
-- BOND_EURO/BOND_GLOBAL have no separate index series, so index positions track
-- the same proxy ETF as exchange-traded positions do.
INSERT INTO benchmark_category_proxy_new (benchmark_category, etf_proxy_isin, index_proxy_isin, index_series_key) VALUES
('EQUITY_DM',   'IE00B4L5Y983', NULL,           'MSCI_WORLD'),
('EQUITY_EM',   'IE00B4L5YC18', NULL,           'MSCI_EM'),
('BOND_EURO',   'IE00B3DKXQ41', 'IE00B3DKXQ41', NULL),
('BOND_GLOBAL', 'IE00BDBRDM35', 'IE00BDBRDM35', NULL);

DROP TABLE benchmark_category_proxy;

ALTER TABLE benchmark_category_proxy_new RENAME TO benchmark_category_proxy;

ALTER TABLE benchmark_category_proxy
    RENAME CONSTRAINT benchmark_category_proxy_new_pkey TO benchmark_category_proxy_pkey;
ALTER TABLE benchmark_category_proxy
    RENAME CONSTRAINT benchmark_category_proxy_new_category_uq TO benchmark_category_proxy_category_uq;
ALTER TABLE benchmark_category_proxy
    RENAME CONSTRAINT benchmark_category_proxy_new_etf_proxy_fkey TO benchmark_category_proxy_etf_proxy_fkey;
ALTER TABLE benchmark_category_proxy
    RENAME CONSTRAINT benchmark_category_proxy_new_index_proxy_fkey TO benchmark_category_proxy_index_proxy_fkey;
ALTER TABLE benchmark_category_proxy
    RENAME CONSTRAINT benchmark_category_proxy_new_index_target_check TO benchmark_category_proxy_index_target_check;

-- A benchmark category may only name a category that actually has a proxy.
-- NULL stays allowed: the proxy ETFs themselves ARE the benchmarks and
-- deliberately carry no category.
ALTER TABLE instrument_reference
    ADD CONSTRAINT instrument_reference_benchmark_category_fkey
        FOREIGN KEY (benchmark_category) REFERENCES benchmark_category_proxy (benchmark_category);

-- =============================================================================
-- Append-only audit of every instrument_reference change. Written by a
-- PostgreSQL trigger (db/pg/V1_249_1) — on H2 the table exists but stays empty.
-- notified_at is stamped once InstrumentValidationJob has mailed the change out.
-- =============================================================================

CREATE TABLE instrument_reference_history (
    id                      bigserial   NOT NULL,
    instrument_reference_id bigint      NOT NULL,
    isin                    varchar(12) NOT NULL,
    operation               varchar(6)  NOT NULL,
    old_values              jsonb,
    new_values              jsonb,
    changed_by              text        NOT NULL,
    changed_at              timestamptz NOT NULL,
    notified_at             timestamptz,

    CONSTRAINT instrument_reference_history_pkey PRIMARY KEY (id),
    CONSTRAINT instrument_reference_history_operation_check
        CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE'))
);

CREATE INDEX instrument_reference_history_unnotified_index
    ON instrument_reference_history (notified_at, id);
