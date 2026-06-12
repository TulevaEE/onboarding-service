CREATE TABLE transaction_settlement (
    id bigserial NOT NULL,
    order_id bigint NOT NULL,
    settled_at timestamptz NOT NULL,
    report_date date NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_transaction_settlement PRIMARY KEY (id),
    CONSTRAINT uq_transaction_settlement_order_id UNIQUE (order_id),
    CONSTRAINT fk_transaction_settlement_order
        FOREIGN KEY (order_id) REFERENCES investment_transaction_order(id)
);

CREATE INDEX idx_transaction_settlement_order_id ON transaction_settlement(order_id);
