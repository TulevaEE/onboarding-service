ALTER TABLE redemption_request ADD COLUMN party_type text;
ALTER TABLE redemption_request ADD COLUMN party_code text;

UPDATE redemption_request
SET party_type = 'PERSON',
    party_code = (SELECT u.personal_code FROM users u WHERE u.id = redemption_request.user_id);

ALTER TABLE redemption_request ALTER COLUMN party_type SET NOT NULL;
ALTER TABLE redemption_request ALTER COLUMN party_code SET NOT NULL;

CREATE INDEX idx_redemption_request_party_status
  ON redemption_request (party_type, party_code, status);
