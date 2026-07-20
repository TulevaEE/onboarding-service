-- A parent_child_link is either ACTIVE (authorizes representation) or PENDING_KYC (captured from
-- the population register when the OTHER guardian has not yet completed their own onboarding/KYC).
-- A PENDING_KYC link must never authorize anything; the "active representation" repository queries
-- filter status = 'ACTIVE'. Existing rows all represent real, authorized links, so backfill ACTIVE.
ALTER TABLE parent_child_link
    ADD COLUMN status text NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE parent_child_link
    ADD CONSTRAINT parent_child_link_status_check CHECK (status IN ('ACTIVE', 'PENDING_KYC'));
