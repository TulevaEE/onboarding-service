ALTER TABLE comparison_fund_values RENAME TO index_values;
ALTER TABLE index_values RENAME COLUMN fund TO key;
ALTER TABLE index_values RENAME COLUMN time TO date;
ALTER TABLE index_values ALTER COLUMN date TYPE date;

