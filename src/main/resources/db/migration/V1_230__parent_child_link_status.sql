-- ACTIVE authorizes representation; PENDING_KYC grants no access. Existing rows are all real links.
CREATE TYPE parent_child_link_status AS ENUM ('ACTIVE', 'PENDING_KYC');

ALTER TABLE parent_child_link
    ADD COLUMN status parent_child_link_status NOT NULL DEFAULT 'ACTIVE';
