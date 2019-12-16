CREATE TABLE crm
(
    id                     SERIAL PRIMARY KEY,
    user_id                INTEGER      NOT NULL REFERENCES users,
    isin                   VARCHAR(255) NOT NULL,
    active_contributions   BOOLEAN      NOT NULL,
    balance                BOOLEAN      NOT NULL,
    transfer_in_date       DATE,
    transfer_out_date      DATE,
    last_contribution_date DATE,
    created_date           TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX crm_user_id_index
    ON crm (user_id);