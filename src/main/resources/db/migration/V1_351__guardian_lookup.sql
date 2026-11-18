CREATE TABLE guardian_lookup (
    child_personal_code text NOT NULL,
    valid_until date NOT NULL,
    looked_up_at timestamptz NOT NULL,
    message_id uuid NOT NULL,
    CONSTRAINT pk_guardian_lookup PRIMARY KEY (child_personal_code)
);

CREATE TABLE guardian_lookup_guardian (
    child_personal_code text NOT NULL,
    guardian_personal_code text NOT NULL,
    custody_type text NOT NULL,
    CONSTRAINT pk_guardian_lookup_guardian
        PRIMARY KEY (child_personal_code, guardian_personal_code, custody_type),
    CONSTRAINT fk_guardian_lookup_guardian_lookup
        FOREIGN KEY (child_personal_code) REFERENCES guardian_lookup (child_personal_code)
);

CREATE INDEX idx_guardian_lookup_guardian_guardian
    ON guardian_lookup_guardian (guardian_personal_code);
