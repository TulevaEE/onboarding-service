CREATE TABLE epis_report_summary (
    id              bigserial   NOT NULL,
    report_id       bigint      NOT NULL,
    report_type     text        NOT NULL,
    report_date     date        NOT NULL,
    fund_code       text        NOT NULL,
    fund_isin       text        NOT NULL,
    data            jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at      timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT epis_report_summary_pkey PRIMARY KEY (id),
    CONSTRAINT fk_epis_summary_report FOREIGN KEY (report_id) REFERENCES investment_report (id),
    CONSTRAINT uq_epis_summary_report_fund UNIQUE (report_id, fund_code)
);

CREATE INDEX idx_epis_summary_type_fund_date ON epis_report_summary (report_type, fund_code, report_date);

CREATE TABLE peva_rava_cycle (
    id              bigserial   NOT NULL,
    lock_date       date        NOT NULL,
    exec_date       date        NOT NULL,
    phase           text        NOT NULL DEFAULT 'IGNORE',
    r17_report_id   bigint,
    r21_report_id   bigint,
    created_at      timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT peva_rava_cycle_pkey PRIMARY KEY (id),
    CONSTRAINT fk_peva_rava_cycle_r17_report FOREIGN KEY (r17_report_id) REFERENCES investment_report (id),
    CONSTRAINT fk_peva_rava_cycle_r21_report FOREIGN KEY (r21_report_id) REFERENCES investment_report (id),
    CONSTRAINT uq_peva_rava_exec UNIQUE (exec_date)
);

CREATE INDEX idx_peva_rava_cycle_r17_report ON peva_rava_cycle (r17_report_id);
CREATE INDEX idx_peva_rava_cycle_r21_report ON peva_rava_cycle (r21_report_id);

INSERT INTO investment_parameter (effective_date, parameter_name, fund_code, numeric_value)
VALUES
    ('2026-06-01', 'R16_BUFFER_PERCENT', NULL, 0.02),
    ('2026-06-01', 'R16_ROUNDING_STEP', NULL, 1000),
    ('2026-06-01', 'PEVA_RAVA_PAYMENT_LIMIT_BUFFER', 'TUK75', 500000),
    ('2026-06-01', 'PEVA_RAVA_PAYMENT_LIMIT_BUFFER', 'TUK00', 200000),
    ('2026-06-01', 'PEVA_RAVA_PAYMENT_LIMIT_ROUNDING_STEP', NULL, 5000),
    ('2026-06-01', 'PEVA_RAVA_TRADE_BUFFER_PERCENT', NULL, 0.02),
    ('2026-06-01', 'PEVA_RAVA_TRADE_ROUNDING_STEP', NULL, 1000);
