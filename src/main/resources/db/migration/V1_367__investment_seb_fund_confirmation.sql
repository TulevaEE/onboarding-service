CREATE TABLE investment_seb_fund_confirmation (
    trade_reference   text        NOT NULL,
    fund_code         text        NOT NULL,
    instrument_isin   varchar(12) NOT NULL,
    transaction_type  text        NOT NULL,
    order_received_at timestamptz NOT NULL,
    pricing_date      date        NOT NULL,
    source_file_key   text        NOT NULL,
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    CONSTRAINT investment_seb_fund_confirmation_pkey PRIMARY KEY (trade_reference)
);
