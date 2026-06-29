ALTER TABLE investment_transaction_execution
    ADD COLUMN settlement_amount numeric(19,2);

ALTER TABLE investment_transaction_order
    ADD COLUMN comment text;
