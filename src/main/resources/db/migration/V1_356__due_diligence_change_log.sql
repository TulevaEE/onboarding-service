CREATE TABLE user_name_change (
    id                 bigserial   NOT NULL,
    user_id            bigint      NOT NULL,
    old_first_name     text,
    old_last_name      text,
    new_first_name     text        NOT NULL,
    new_last_name      text        NOT NULL,
    source             text        NOT NULL,
    document_reference text,
    changed_at         timestamptz NOT NULL,
    CONSTRAINT pk_user_name_change PRIMARY KEY (id),
    CONSTRAINT fk_user_name_change_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX ix_user_name_change_user_id ON user_name_change (user_id);

CREATE INDEX ix_user_name_change_changed_at ON user_name_change (changed_at);

ALTER TABLE kyc_survey ADD COLUMN submitted_by_user_id bigint;

ALTER TABLE kyc_survey
    ADD CONSTRAINT fk_kyc_survey_submitted_by_user
    FOREIGN KEY (submitted_by_user_id) REFERENCES users (id);

CREATE INDEX ix_kyc_survey_submitted_by_user_id ON kyc_survey (submitted_by_user_id);
