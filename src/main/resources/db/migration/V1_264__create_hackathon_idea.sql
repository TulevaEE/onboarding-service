CREATE TABLE hackathon_idea (
    id bigserial NOT NULL,
    user_id bigint NOT NULL,
    challenge text NOT NULL,
    problem text NOT NULL,
    solution text NOT NULL,
    progress text,
    needed_skills jsonb NOT NULL,
    additional_info text,
    created_time timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_hackathon_idea PRIMARY KEY (id),
    CONSTRAINT fk_hackathon_idea_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_hackathon_idea_user_id ON hackathon_idea (user_id);
