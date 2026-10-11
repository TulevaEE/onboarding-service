-- One row per payment started through a gift link, which is what makes a payment a gift rather
-- than the parent's own deposit: both arrive as third-party deposits into the child's account and
-- nothing on the payment itself tells them apart.
--
-- Joined to the money by the payment description, which is the only thing that survives both ways
-- a payment can reach us: Montonio's callback and, when Montonio sends no sender details, the bank
-- statement hours later. Nothing else is common to both.
CREATE TABLE savings_fund_gift
(
    id           UUID           NOT NULL DEFAULT gen_random_uuid(),
    gift_link_id UUID           NOT NULL,
    -- Matches saving_fund_payment.description. Not unique: two gifts to the same child in the same
    -- second would collide, which a determined visitor could arrange. Reading is written to notice
    -- that and show no message rather than the wrong one.
    description  TEXT           NOT NULL,
    amount       DECIMAL(15, 2) NOT NULL,
    -- Null when the giver wrote nothing; the row still records that a gift was started.
    message      TEXT,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT savings_fund_gift_pk PRIMARY KEY (id),
    CONSTRAINT savings_fund_gift_link_fk
        FOREIGN KEY (gift_link_id) REFERENCES savings_fund_gift_link (id)
);

CREATE INDEX idx_savings_fund_gift_description
    ON savings_fund_gift (description);

CREATE INDEX idx_savings_fund_gift_link
    ON savings_fund_gift (gift_link_id);
