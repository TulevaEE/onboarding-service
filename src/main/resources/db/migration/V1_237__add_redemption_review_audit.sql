ALTER TABLE redemption_request ADD COLUMN reviewed_by TEXT;
ALTER TABLE redemption_request ADD COLUMN review_reason TEXT;
ALTER TABLE redemption_request ADD COLUMN reviewed_at TIMESTAMPTZ;
