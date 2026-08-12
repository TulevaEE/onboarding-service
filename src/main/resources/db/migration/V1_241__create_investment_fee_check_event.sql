CREATE TABLE investment_fee_check_event (
    id               bigserial   NOT NULL,
    fund_code        text        NOT NULL,
    check_date       date        NOT NULL,
    fee_month        date,
    check_type       text        NOT NULL,
    fee_scope        text        NOT NULL,
    severity         text        NOT NULL,
    deviation_found  boolean     NOT NULL DEFAULT false,
    deviation_amount numeric(19, 6),
    alert_failed     boolean     NOT NULL DEFAULT false,
    result           jsonb       NOT NULL DEFAULT '{}',
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT investment_fee_check_event_pkey PRIMARY KEY (id)
);

CREATE INDEX investment_fee_check_event_diff_idx
    ON investment_fee_check_event (fund_code, check_type, fee_scope, fee_month, created_at DESC);
CREATE INDEX investment_fee_check_event_fund_month_idx
    ON investment_fee_check_event (fund_code, fee_month);
CREATE INDEX investment_fee_check_event_deviation_idx
    ON investment_fee_check_event (deviation_found);
