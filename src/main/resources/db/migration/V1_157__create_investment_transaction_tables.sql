CREATE TABLE investment_transaction_batch (
    id bigserial NOT NULL,
    fund_code text not null,
    created_at timestamptz not null default now(),
    created_by text not null,
    status text not null default 'AWAITING_CONFIRMATION',
    metadata jsonb not null default '{}',
    constraint investment_transaction_batch_pkey primary key (id)
);

CREATE TABLE investment_transaction_order (
    id bigserial NOT NULL,
    batch_id bigint not null,
    fund_code text not null,
    instrument_isin text not null,
    transaction_type text not null,
    instrument_type text not null,
    order_amount numeric(19,2),
    order_quantity bigint,
    order_venue text not null,
    trader_id text,
    order_uuid uuid not null,
    order_status text not null default 'PENDING',
    order_type text not null default 'MOC',
    order_timestamp timestamptz,
    expected_settlement_date date,
    created_at timestamptz not null default now(),
    constraint investment_transaction_order_pkey primary key (id),
    constraint fk_transaction_order_batch
        foreign key (batch_id) references investment_transaction_batch(id)
);

CREATE INDEX idx_transaction_order_batch_id ON investment_transaction_order(batch_id);
CREATE UNIQUE INDEX idx_transaction_order_uuid ON investment_transaction_order(order_uuid);
CREATE INDEX idx_transaction_order_status ON investment_transaction_order(order_status);

CREATE INDEX idx_transaction_batch_status ON investment_transaction_batch(status);

CREATE TABLE investment_transaction_audit_event (
    id bigserial NOT NULL,
    batch_id bigint not null,
    event_type text not null,
    actor text,
    payload jsonb not null default '{}',
    created_at timestamptz not null default now(),
    constraint investment_transaction_audit_event_pkey primary key (id),
    constraint fk_audit_event_batch
        foreign key (batch_id) references investment_transaction_batch(id)
);

CREATE INDEX idx_audit_event_batch_id ON investment_transaction_audit_event(batch_id);

CREATE TABLE investment_transaction_command (
    id bigserial NOT NULL,
    fund_code text not null,
    mode text not null,
    as_of_date date not null,
    manual_adjustments jsonb not null default '{}',
    status text not null default 'PENDING',
    error_message text,
    batch_id bigint,
    created_at timestamptz not null default now(),
    processed_at timestamptz,
    constraint investment_transaction_command_pkey primary key (id),
    constraint fk_transaction_command_batch
        foreign key (batch_id) references investment_transaction_batch(id)
);

CREATE INDEX idx_transaction_command_status ON investment_transaction_command(status);
CREATE INDEX idx_transaction_command_batch_id ON investment_transaction_command(batch_id);

ALTER TABLE investment_model_portfolio_allocation ADD COLUMN instrument_type text;
ALTER TABLE investment_model_portfolio_allocation ADD COLUMN order_venue text;
ALTER TABLE investment_model_portfolio_allocation ADD COLUMN fast_sell boolean NOT NULL DEFAULT false;
ALTER TABLE investment_model_portfolio_allocation ADD COLUMN bbg_ticker text;
