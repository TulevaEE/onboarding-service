CREATE TABLE redemption_request (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    fund_units DECIMAL(15,5) NOT NULL,
    customer_iban VARCHAR(34) NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    cancelled_at TIMESTAMPTZ,
    processed_at TIMESTAMPTZ,

    cash_amount DECIMAL(15,2),
    nav_per_unit DECIMAL(15,5),

    error_reason TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_redemption_request_user_id ON redemption_request(user_id);
CREATE INDEX idx_redemption_request_status_requested_at ON redemption_request(status, requested_at);
