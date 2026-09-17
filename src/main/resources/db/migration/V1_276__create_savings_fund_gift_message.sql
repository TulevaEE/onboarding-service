-- The few words a giver writes for the child, kept apart from the payment because the payment is
-- the bank's record and this is not.
--
-- Joined to the money by the payment description, which is the only thing that survives both ways
-- a payment can reach us: Montonio's callback and, when Montonio sends no sender details, the bank
-- statement hours later. Nothing else is common to both.
CREATE TABLE savings_fund_gift_message
(
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    gift_link_id UUID        NOT NULL,
    -- Matches saving_fund_payment.description. Not unique: two gifts to the same child in the same
    -- second would collide, which a determined visitor could arrange. Reading is written to notice
    -- that and show no message rather than the wrong one.
    description  TEXT        NOT NULL,
    amount       DECIMAL(15, 2) NOT NULL,
    message      TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT savings_fund_gift_message_pk PRIMARY KEY (id),
    CONSTRAINT savings_fund_gift_message_link_fk
        FOREIGN KEY (gift_link_id) REFERENCES savings_fund_gift_link (id)
);

CREATE INDEX idx_savings_fund_gift_message_description
    ON savings_fund_gift_message (description);

CREATE INDEX idx_savings_fund_gift_message_link
    ON savings_fund_gift_message (gift_link_id);
