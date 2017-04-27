CREATE TABLE IF NOT EXISTS oauth_client_details (
  client_id VARCHAR(256) PRIMARY KEY,
  resource_ids VARCHAR(256),
  client_secret VARCHAR(256),
  scope VARCHAR(256),
  authorized_grant_types VARCHAR(256),
  web_server_redirect_uri VARCHAR(256),
  authorities VARCHAR(256),
  access_token_validity INTEGER,
  refresh_token_validity INTEGER,
  additional_information VARCHAR(4096),
  autoapprove VARCHAR(256)
);

CREATE TABLE IF NOT EXISTS oauth_access_token (
  token_id VARCHAR(256) PRIMARY KEY,
  token bytea,
  authentication_id VARCHAR(256),
  user_name VARCHAR(256),
  client_id VARCHAR(256),
  authentication bytea,
  refresh_token VARCHAR(256)
);

CREATE TABLE IF NOT EXISTS oauth_refresh_token (
  token_id VARCHAR(256) REFERENCES oauth_access_token,
  token bytea,
  authentication bytea
);

CREATE TABLE IF NOT EXISTS users (
  id SERIAL PRIMARY KEY,
  active BOOLEAN NOT NULL,
  personal_code CHAR(11) NOT NULL,
  first_name VARCHAR(255) NOT NULL,
  last_name VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,
  phone_number VARCHAR(255),
  member_number INTEGER NOT NULL,
  created_date TIMESTAMP NOT NULL,
  updated_date TIMESTAMP NOT NULL,
  CONSTRAINT personal_code UNIQUE (personal_code),
  CONSTRAINT member_number UNIQUE (member_number)
);

CREATE TABLE IF NOT EXISTS initial_capital (
  id SERIAL PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users,
  amount DECIMAL(12,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  CONSTRAINT user_id UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS fund_manager (
  id SERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  CONSTRAINT name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS fund (
  id SERIAL PRIMARY KEY,
  isin VARCHAR(255) NOT NULL,
  name TEXT NOT NULL,
  management_fee_rate DECIMAL(12,7) NOT NULL,
  fund_manager_id INTEGER NOT NULL REFERENCES fund_manager,
  CONSTRAINT isin UNIQUE (isin)
);

CREATE TABLE IF NOT EXISTS mandate (
  id SERIAL PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users,
  future_contribution_fund_isin VARCHAR NOT NULL,
  mandate bytea,
  created_date TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS fund_transfer_exchange (
  id SERIAL PRIMARY KEY,
  mandate_id INTEGER NOT NULL REFERENCES mandate,
  amount DECIMAL(4,2) NOT NULL,
  source_fund_isin VARCHAR NOT NULL,
  target_fund_isin VARCHAR NOT NULL
);

CREATE TABLE IF NOT EXISTS fund_transfer_statistics (
  id SERIAL PRIMARY KEY,
  value DECIMAL(12,2) NOT NULL,
  transferred DECIMAL(12,2) NOT NULL,
  isin VARCHAR NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS fund_value_statistics (
  id SERIAL PRIMARY KEY,
  isin VARCHAR NOT NULL,
  value DECIMAL(12,2) NOT NULL,
  identifier UUID NOT NULL,
  created_date TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS mandate_process (
  id SERIAL PRIMARY KEY,
  mandate_id INTEGER NOT NULL REFERENCES mandate,
  process_id VARCHAR NOT NULL,
  type VARCHAR NOT NULL,
  successful BOOLEAN,
  error_code integer,
  created_date TIMESTAMP NOT NULL
);
