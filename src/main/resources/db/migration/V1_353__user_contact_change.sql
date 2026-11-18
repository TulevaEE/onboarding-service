CREATE TABLE user_contact_change (
    id         bigserial   NOT NULL,
    user_id    bigint      NOT NULL,
    field      text        NOT NULL,
    old_value  text,
    new_value  text,
    source     text        NOT NULL,
    changed_at timestamptz NOT NULL,
    CONSTRAINT pk_user_contact_change PRIMARY KEY (id),
    CONSTRAINT fk_user_contact_change_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX ix_user_contact_change_user_id ON user_contact_change (user_id);

CREATE INDEX ix_user_contact_change_changed_at ON user_contact_change (changed_at);
