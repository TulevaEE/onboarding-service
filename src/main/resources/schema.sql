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
  personal_code CHAR(11) NOT NULL,
  first_name VARCHAR(255) NOT NULL,
  last_name VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,
  phone_number VARCHAR(255) NOT NULL,
  created_date TIMESTAMP NOT NULL,
  member_number INTEGER NOT NULL,
  CONSTRAINT personal_code UNIQUE (personal_code)
);

CREATE TABLE IF NOT EXISTS initial_capital (
  id SERIAL PRIMARY KEY,
  user_id INTEGER REFERENCES users,
  amount DECIMAL(12,2),
  currency VARCHAR(3)
);

CREATE TABLE IF NOT EXISTS fund_manager (
  id SERIAL PRIMARY KEY,
  name TEXT
);

CREATE TABLE IF NOT EXISTS pension_funds (
  id SERIAL PRIMARY KEY,
  isin TEXT,
  name TEXT,
  management_fee_percent REAL,
  fund_manager INTEGER REFERENCES fund_manager
);

