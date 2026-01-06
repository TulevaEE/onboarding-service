ALTER TABLE index_values ADD COLUMN provider TEXT;
ALTER TABLE index_values ADD COLUMN updated_at TIMESTAMP;

UPDATE index_values SET provider = 'YAHOO' WHERE key LIKE '%.DE' OR key LIKE '%.PA' OR key LIKE '%.F';
UPDATE index_values SET provider = 'PENSIONIKESKUS' WHERE key LIKE 'EPI%' OR key LIKE 'AUM_%' OR key LIKE 'EE%';
UPDATE index_values SET provider = 'EUROSTAT' WHERE key = 'CPI';
UPDATE index_values SET provider = 'MORNINGSTAR' WHERE key = 'GLOBAL_STOCK_INDEX';
UPDATE index_values SET provider = 'MSCI' WHERE key = 'MSCI_ACWI';
UPDATE index_values SET provider = 'GOOGLE_SHEETS' WHERE key = 'MARKET';
UPDATE index_values SET provider = 'CALCULATED' WHERE provider IS NULL;

UPDATE index_values SET updated_at = date::timestamp WHERE updated_at IS NULL;

ALTER TABLE index_values ALTER COLUMN provider SET NOT NULL;
ALTER TABLE index_values ALTER COLUMN updated_at SET NOT NULL;
