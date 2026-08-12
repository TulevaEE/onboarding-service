CREATE TABLE hackathon_registration (
    id bigserial NOT NULL,
    user_id bigint NOT NULL,
    email text NOT NULL,
    phone_number text,
    role text NOT NULL,
    skills jsonb NOT NULL,
    challenges jsonb NOT NULL,
    participation text NOT NULL,
    idea text,
    linkedin_url text,
    created_time timestamptz NOT NULL DEFAULT now(),
    updated_time timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_hackathon_registration PRIMARY KEY (id),
    CONSTRAINT uq_hackathon_registration_user_id UNIQUE (user_id),
    CONSTRAINT fk_hackathon_registration_user FOREIGN KEY (user_id) REFERENCES users (id)
);
