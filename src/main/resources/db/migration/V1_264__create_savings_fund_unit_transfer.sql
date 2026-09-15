-- A transfer is recorded when it is submitted and only moves units once a second person approves it,
-- so the row exists in AWAITING_APPROVAL before anything reaches the ledger.
CREATE TYPE savings_fund_unit_transfer_state AS ENUM ('AWAITING_APPROVAL', 'EXECUTED', 'CANCELLED');

CREATE TABLE savings_fund_unit_transfer
(
    id                             UUID                             NOT NULL DEFAULT gen_random_uuid(),
    from_party_code                TEXT                             NOT NULL,
    from_party_type                ledger.party_type                NOT NULL,
    to_party_code                  TEXT                             NOT NULL,
    to_party_type                  ledger.party_type                NOT NULL,
    fund_units                     DECIMAL(15, 5)                   NOT NULL,

    -- What the recipient may deduct when they sell. An heir's is their own expense and a buyer's is
    -- the price they paid, neither of which Tuleva sees, so this is recorded and never derived.
    recipient_acquisition_cost_eur DECIMAL(15, 2),

    notified_at                    DATE                             NOT NULL,
    evidence                       TEXT                             NOT NULL,
    plan_hash                      TEXT                             NOT NULL,
    state                          savings_fund_unit_transfer_state NOT NULL DEFAULT 'AWAITING_APPROVAL',
    submitted_by                   TEXT                             NOT NULL,
    approved_by                    TEXT,
    ledger_transaction_id          UUID,
    created_at                     TIMESTAMPTZ                      NOT NULL DEFAULT NOW(),
    executed_at                    TIMESTAMPTZ,
    cancelled_at                   TIMESTAMPTZ,

    CONSTRAINT savings_fund_unit_transfer_pk PRIMARY KEY (id),
    CONSTRAINT savings_fund_unit_transfer_approved_by_differs
        CHECK (approved_by IS NULL OR approved_by <> submitted_by)
);

CREATE INDEX idx_savings_fund_unit_transfer_state
    ON savings_fund_unit_transfer (state, created_at);
