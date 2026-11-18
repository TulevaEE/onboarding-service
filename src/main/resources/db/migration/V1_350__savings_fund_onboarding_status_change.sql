CREATE TABLE savings_fund_onboarding_status_change (
    id         bigserial   NOT NULL,
    code       text        NOT NULL,
    type       text        NOT NULL,
    old_status text,
    new_status text        NOT NULL,
    reason     text        NOT NULL,
    changed_at timestamptz NOT NULL,
    CONSTRAINT pk_savings_fund_onboarding_status_change PRIMARY KEY (id)
);

CREATE INDEX ix_savings_fund_onboarding_status_change_code_type
    ON savings_fund_onboarding_status_change (code, type);
