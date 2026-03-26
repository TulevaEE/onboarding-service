ALTER TABLE saving_fund_payment ADD COLUMN party_type text;
ALTER TABLE saving_fund_payment ADD COLUMN party_code text;

UPDATE saving_fund_payment
SET party_type = 'PERSON',
    party_code = (SELECT u.personal_code FROM users u WHERE u.id = saving_fund_payment.user_id);

CREATE INDEX idx_saving_fund_payment_party ON saving_fund_payment (party_type, party_code);
