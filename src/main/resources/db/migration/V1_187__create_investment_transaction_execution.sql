CREATE TABLE investment_transaction_execution (
    id bigserial NOT NULL,
    order_id bigint NOT NULL,
    broker_transaction_id text,
    aggregated_order_id uuid,
    execution_timestamp timestamptz,
    executed_quantity numeric(19,4),
    unit_price numeric(19,8),
    total_consideration numeric(19,2),
    commission_amount numeric(19,2),
    settlement_fee_amount numeric(19,2),
    settlement_penalty numeric(19,2),
    net_settlement_amount numeric(19,2),
    actual_settlement_date date,
    nav_date date,
    comment text,
    source text NOT NULL,
    source_file_key text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    modified_by text,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT pk_investment_transaction_execution PRIMARY KEY (id),
    CONSTRAINT fk_investment_transaction_execution_order
        FOREIGN KEY (order_id) REFERENCES investment_transaction_order(id),
    CONSTRAINT uk_investment_transaction_execution_order UNIQUE (order_id)
);

CREATE INDEX ix_investment_transaction_execution_broker_tx
    ON investment_transaction_execution(broker_transaction_id);
CREATE INDEX ix_investment_transaction_execution_settlement_date
    ON investment_transaction_execution(actual_settlement_date);
