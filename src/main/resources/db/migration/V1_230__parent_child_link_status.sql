-- ACTIVE authorizes representation; PENDING_KYC grants no access. Existing rows are all real links.
ALTER TABLE parent_child_link
    ADD COLUMN status text NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE parent_child_link
    ADD CONSTRAINT parent_child_link_status_check CHECK (status IN ('ACTIVE', 'PENDING_KYC'));
