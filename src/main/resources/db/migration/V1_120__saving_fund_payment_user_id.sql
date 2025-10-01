ALTER TABLE saving_fund_payment ADD COLUMN user_id INTEGER REFERENCES users(id);
