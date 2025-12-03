ALTER TABLE redemption_request
DROP COLUMN created_at;

ALTER TABLE redemption_request
ADD COLUMN requested_amount DECIMAL(15, 2) NOT NULL;
