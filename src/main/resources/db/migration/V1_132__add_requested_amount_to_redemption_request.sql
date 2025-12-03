ALTER TABLE redemption_request
DROP COLUMN created_at;

ALTER TABLE redemption_request
ADD COLUMN requested_amount DECIMAL(15, 2);

UPDATE redemption_request
SET requested_amount = CAST(fund_units AS DECIMAL(15, 2));

ALTER TABLE redemption_request
ALTER COLUMN requested_amount SET NOT NULL;
