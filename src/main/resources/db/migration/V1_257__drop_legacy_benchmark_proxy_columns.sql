-- Contract half of the benchmark proxy re-key started in V1_249.
--
-- V1_249 added etf_proxy_isin / index_proxy_isin / index_series_key and backfilled them, but kept
-- etf_proxy_storage_key and index_proxy_key because the release deployed at the time still selected
-- them. Nothing reads them now, so they can go.
--
-- Do not merge this until the V1_249 release is deployed everywhere and a rollback to the previous
-- release is no longer a possibility: that release cannot start without these columns.

ALTER TABLE benchmark_category_proxy DROP COLUMN IF EXISTS etf_proxy_storage_key;
ALTER TABLE benchmark_category_proxy DROP COLUMN IF EXISTS index_proxy_key;
