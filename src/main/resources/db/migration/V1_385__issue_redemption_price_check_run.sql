CREATE TABLE issue_redemption_price_check_run (
    id                bigserial   NOT NULL,
    first_booking_day date        NOT NULL,
    last_booking_day  date        NOT NULL,
    deals             integer     NOT NULL,
    reported          integer     NOT NULL,
    checked_at        timestamptz NOT NULL,
    CONSTRAINT issue_redemption_price_check_run_pkey PRIMARY KEY (id)
);

CREATE INDEX issue_redemption_price_check_run_last_booking_day_idx
    ON issue_redemption_price_check_run (last_booking_day DESC);
