CREATE TABLE scheduled_email
(
    id                  SERIAL PRIMARY KEY,
    user_id             INTEGER      NOT NULL REFERENCES users,
    mandrill_message_id VARCHAR(255),
    type                VARCHAR(255) NOT NULL
);

CREATE INDEX scheduled_email_user_id_index ON scheduled_email (user_id);
