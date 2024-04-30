CREATE TABLE scheduled_email
(
    id                  SERIAL PRIMARY KEY,
    user_id             INTEGER      NOT NULL,
    mandrill_message_id VARCHAR(255) NOT NULL,
    type                VARCHAR(255) NOT NULL,
    CONSTRAINT scheduled_email_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX scheduled_email_user_id_index ON scheduled_email (user_id);
