CREATE TABLE fee_rate (
    id              bigserial NOT NULL,
    fund_code       text NOT NULL,
    fee_type        text NOT NULL,
    annual_rate     numeric(10, 8) NOT NULL,
    valid_from      date NOT NULL,
    valid_to        date,
    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      text,

    CONSTRAINT fee_rate_pkey PRIMARY KEY (id),
    CONSTRAINT fee_rate_fund_code_fee_type_valid_from_key
        UNIQUE (fund_code, fee_type, valid_from)
);

CREATE INDEX fee_rate_fund_code_fee_type_idx ON fee_rate (fund_code, fee_type);
CREATE INDEX fee_rate_valid_from_idx ON fee_rate (valid_from);

CREATE TABLE fee_accrual (
    id                  bigserial NOT NULL,
    fund_code           text NOT NULL,
    fee_type            text NOT NULL,
    accrual_date        date NOT NULL,
    fee_month           date NOT NULL,
    base_value          numeric(19, 2) NOT NULL,
    annual_rate         numeric(10, 8) NOT NULL,
    daily_amount_net    numeric(19, 6) NOT NULL,
    daily_amount_gross  numeric(19, 6) NOT NULL,
    vat_rate            numeric(5, 4),
    days_in_year        int NOT NULL,
    reference_date      date,
    created_at          timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fee_accrual_pkey PRIMARY KEY (id),
    CONSTRAINT fee_accrual_fund_code_fee_type_accrual_date_key
        UNIQUE (fund_code, fee_type, accrual_date)
);

CREATE INDEX fee_accrual_fee_month_idx ON fee_accrual (fee_month);
CREATE INDEX fee_accrual_fund_code_fee_type_fee_month_idx
    ON fee_accrual (fund_code, fee_type, fee_month);

CREATE TABLE depot_fee_tier (
    id              bigserial NOT NULL,
    min_aum         numeric(19, 2) NOT NULL,
    annual_rate     numeric(10, 8) NOT NULL,
    valid_from      date NOT NULL,
    valid_to        date,
    created_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT depot_fee_tier_pkey PRIMARY KEY (id),
    CONSTRAINT depot_fee_tier_min_aum_valid_from_key
        UNIQUE (min_aum, valid_from)
);

CREATE TABLE custody_fee_instrument_type (
    id              bigserial NOT NULL,
    isin            text NOT NULL,
    instrument_type text NOT NULL,
    annual_rate     numeric(10, 8),
    valid_from      date NOT NULL,
    valid_to        date,
    created_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT custody_fee_instrument_type_pkey PRIMARY KEY (id),
    CONSTRAINT custody_fee_instrument_type_isin_valid_from_key
        UNIQUE (isin, valid_from)
);
