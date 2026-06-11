-- View 2 columns: fund-vs-benchmark layer
ALTER TABLE investment_td_attribution ADD COLUMN etf_ocf_drag numeric(12, 8);
ALTER TABLE investment_td_attribution ADD COLUMN etf_tracking_residual numeric(12, 8);
ALTER TABLE investment_td_attribution ADD COLUMN td_vs_benchmark numeric(12, 8);

-- Indexes for transaction cost commission aggregation query
CREATE INDEX idx_txn_execution_nav_date ON investment_transaction_execution(nav_date);
CREATE INDEX idx_txn_order_fund_code ON investment_transaction_order(fund_code);
