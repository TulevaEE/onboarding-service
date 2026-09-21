CREATE TABLE nudge_exposure (
    id           bigserial NOT NULL,
    user_id      bigint NOT NULL,
    nudge_key    text NOT NULL,
    season_year  integer NOT NULL,
    arm          text NOT NULL,
    assigned_at  timestamptz NOT NULL,
    dismissed_at timestamptz,
    CONSTRAINT pk_nudge_exposure PRIMARY KEY (id),
    CONSTRAINT fk_nudge_exposure_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_nudge_exposure_user_key_season UNIQUE (user_id, nudge_key, season_year)
);

CREATE INDEX idx_nudge_exposure_user_id ON nudge_exposure (user_id);
