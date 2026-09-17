-- A gift link is a capability: whoever holds the token may initiate a payment to the child it
-- names, without logging in. The link itself carries no authorisation beyond that, and it is
-- deliberately not a place where eligibility is enforced. Whether a third-party deposit is
-- accepted is decided when the money arrives, by the same rule that governs every other
-- third-party deposit, so a child who has since turned 18 has their gift returned rather than
-- the link quietly going dead.
CREATE TABLE savings_fund_gift_link
(
    id                       UUID        NOT NULL DEFAULT gen_random_uuid(),
    -- Stored as it is handed out, not hashed. This is a share link, not a credential: the parent
    -- has to be able to reopen their page next year and copy the same address again, which a hash
    -- would make impossible. What it grants is paying money *into* a child's account, so the
    -- exposure from a leaked row is bounded; it is kept out of logs and the page is served
    -- no-store and noindex instead.
    token                    TEXT        NOT NULL,
    recipient_personal_code  TEXT        NOT NULL,
    created_by_personal_code TEXT        NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Closing a link is the parent's emergency brake, not routine housekeeping: the link is meant
    -- to keep working for years, because a grandparent who saved it should not have to ask for a
    -- new one before every birthday.
    closed_at                TIMESTAMPTZ,
    -- Mirrors recipient_personal_code while the link is open and is NULL once it is closed. Since
    -- NULLs do not collide in a unique constraint, this permits any number of closed links but only
    -- one open one per child, so the parent cannot end up handing out two live links by clicking
    -- twice. A partial index would say this more directly, but H2, which the tests run on, does not
    -- have them, and no other migration in this repository uses one.
    open_for_recipient       TEXT,

    CONSTRAINT savings_fund_gift_link_pk PRIMARY KEY (id),
    CONSTRAINT savings_fund_gift_link_token_unique UNIQUE (token),
    CONSTRAINT savings_fund_gift_link_one_open_per_recipient UNIQUE (open_for_recipient)
);

CREATE INDEX idx_savings_fund_gift_link_recipient
    ON savings_fund_gift_link (recipient_personal_code);
