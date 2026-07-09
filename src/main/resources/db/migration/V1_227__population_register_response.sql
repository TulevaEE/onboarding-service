CREATE TABLE population_register_response (
    id bigserial NOT NULL,
    personal_code text NOT NULL,
    query_type text NOT NULL,
    message_id uuid NOT NULL,
    response jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT population_register_response_pkey PRIMARY KEY (id)
);

CREATE INDEX idx_population_register_response_lookup
    ON population_register_response (personal_code, query_type, created_at DESC);
