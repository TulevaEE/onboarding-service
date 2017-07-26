CREATE TABLE audit_log
(
    id SERIAL PRIMARY KEY NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    principal VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    data TEXT
);
