ALTER TABLE investment_fee_check_event
    ADD COLUMN alert_failed boolean NOT NULL DEFAULT false;
