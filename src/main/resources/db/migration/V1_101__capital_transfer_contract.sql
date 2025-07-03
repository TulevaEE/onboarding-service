CREATE TABLE capital_transfer_contract (
                                         id BIGSERIAL PRIMARY KEY,
                                         seller_member_id BIGINT NOT NULL,
                                         buyer_personal_code VARCHAR(255) NOT NULL,
                                         iban VARCHAR(255) NOT NULL,
                                         unit_price NUMERIC(19, 4) NOT NULL, -- Using a higher precision for financial values
                                         unit_count INTEGER NOT NULL,
                                         share_type VARCHAR(255) NOT NULL,
                                         status VARCHAR(255) NOT NULL,
                                         buyer_member_id BIGINT,
                                         original_content BYTEA NOT NULL,
                                         digi_doc_container BYTEA NOT NULL,
                                         created_at TIMESTAMP WITH TIME ZONE,
                                         updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_capital_transfer_contract_seller_member_id ON capital_transfer_contract (seller_member_id);
CREATE INDEX idx_capital_transfer_contract_buyer_personal_code ON capital_transfer_contract (buyer_personal_code);
CREATE INDEX idx_capital_transfer_contract_status ON capital_transfer_contract (status);

