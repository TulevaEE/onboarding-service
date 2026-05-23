CREATE TABLE parent_child_link (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    parent_personal_code text NOT NULL,
    child_personal_code text NOT NULL,
    relationship_type text NOT NULL,
    valid_until date NOT NULL,
    created_date timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_parent_child_link UNIQUE (parent_personal_code, child_personal_code, relationship_type)
);

CREATE INDEX idx_parent_child_link_parent ON parent_child_link(parent_personal_code);
CREATE INDEX idx_parent_child_link_child ON parent_child_link(child_personal_code);
