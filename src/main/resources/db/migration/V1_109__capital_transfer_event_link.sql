CREATE TABLE capital_transfer_event_link (
    id BIGSERIAL PRIMARY KEY,
    capital_transfer_contract_id BIGINT NOT NULL,
    member_capital_event_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_capital_transfer_event_contract
        FOREIGN KEY (capital_transfer_contract_id)
        REFERENCES capital_transfer_contract (id),
    
    CONSTRAINT fk_capital_transfer_event_member_event
        FOREIGN KEY (member_capital_event_id)
        REFERENCES member_capital_event (id),
    
    CONSTRAINT uk_capital_transfer_event_link
        UNIQUE (member_capital_event_id)
);

CREATE INDEX idx_capital_transfer_event_link_contract_id 
    ON capital_transfer_event_link (capital_transfer_contract_id);

CREATE INDEX idx_capital_transfer_event_link_member_event_id 
    ON capital_transfer_event_link (member_capital_event_id);
