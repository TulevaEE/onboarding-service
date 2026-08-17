-- =============================================================================
-- price_source_priority: the price-source tie-break order, published for SQL consumers.
--
-- The ordering itself stays owned by PriorityPriceProvider.PRICE_FEEDS in Java —
-- this table is a PUBLISHED COPY, rewritten from that list on every application
-- start (PriceSourcePriorityPublisher). Do not edit it by hand: change PRICE_FEEDS
-- and deploy, and the table follows.
--
-- It exists because the NAV price query in the tuleva repo
-- (work/investeerimistegevus/apps/nav-calc/instrumentide-värsked-hinnad.sql) ranked
-- sources with its own hardcoded CASE, which had drifted out of step with the Java:
-- for an ETF on the same date the query preferred the exchange price while
-- PriorityPriceProvider preferred EODHD. Reading the order from here means the sheet
-- and the service can no longer disagree about which source wins.
--
-- The seed below matches PRICE_FEEDS at the time of writing, so the table is usable
-- before the app has started once.
-- =============================================================================

CREATE TABLE price_source_priority (
    price_source varchar(30)  NOT NULL,
    rank         int          NOT NULL,
    updated_at   timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT price_source_priority_pkey PRIMARY KEY (price_source),
    CONSTRAINT price_source_priority_rank_uq UNIQUE (rank)
);

COMMENT ON TABLE price_source_priority IS
    'Published copy of PriorityPriceProvider.PRICE_FEEDS ordering. Rewritten on app start; do not edit by hand.';
COMMENT ON COLUMN price_source_priority.rank IS
    'Lower wins. Applied only after the price date — a fresher price from a lower-ranked source still wins.';

INSERT INTO price_source_priority (price_source, rank) VALUES
    ('BLACKROCK',       1),
    ('MORNINGSTAR',     2),
    ('EODHD',           3),
    ('DEUTSCHE_BOERSE', 4),
    ('EURONEXT',        5),
    ('YAHOO',           6);
