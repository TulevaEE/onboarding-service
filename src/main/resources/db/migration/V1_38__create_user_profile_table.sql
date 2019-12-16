CREATE TABLE user_profile
(
    id            SERIAL PRIMARY KEY,
    user_id       INTEGER   NOT NULL REFERENCES users,
    language      VARCHAR(3),
    date_of_death DATE,
    created_date  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT user_id UNIQUE (user_id)
);

CREATE INDEX user_profile_user_id_index
    ON user_profile (user_id);