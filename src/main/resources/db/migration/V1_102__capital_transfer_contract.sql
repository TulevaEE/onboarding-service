CREATE TABLE capital_transfer_contract (
                                         id BIGSERIAL PRIMARY KEY,
                                         seller_id BIGINT NOT NULL,
                                         buyer_id BIGINT NOT NULL,
                                         iban VARCHAR(255) NOT NULL,
                                         unit_price NUMERIC(19, 8) NOT NULL,
                                         unit_count INTEGER NOT NULL,
                                         share_type VARCHAR(255) NOT NULL,
                                         state VARCHAR(255) NOT NULL,
                                         original_content BYTEA NOT NULL,
                                         digi_doc_container BYTEA,
                                         created_at TIMESTAMP WITH TIME ZONE,
                                         updated_at TIMESTAMP WITH TIME ZONE,

                                         CONSTRAINT fk_capital_transfer_contract_seller
                                           FOREIGN KEY (seller_id)
                                             REFERENCES member (id),

                                         CONSTRAINT fk_capital_transfer_contract_buyer
                                           FOREIGN KEY (buyer_id)
                                             REFERENCES member (id)
);

CREATE INDEX idx_capital_transfer_contract_state ON capital_transfer_contract (state);
CREATE INDEX idx_capital_transfer_contract_seller_id ON capital_transfer_contract (seller_id);
CREATE INDEX idx_capital_transfer_contract_buyer_id ON capital_transfer_contract (buyer_id);
