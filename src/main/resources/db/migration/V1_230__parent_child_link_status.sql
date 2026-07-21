-- ACTIVE authorizes representation; PENDING_KYC grants no access. Existing rows are all real links.
ALTER TABLE parent_child_link
    ADD COLUMN status text NOT NULL DEFAULT 'ACTIVE';

-- = ANY(ARRAY[...]) instead of IN: on H2 2.4, an ALTER-added CHECK with a literal IN-list breaks
-- with "Check constraint invalid" for the whole test database once the migrating session closes.
ALTER TABLE parent_child_link
    ADD CONSTRAINT parent_child_link_status_check CHECK (status = ANY (ARRAY ['ACTIVE', 'PENDING_KYC']));
