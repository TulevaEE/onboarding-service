ALTER TABLE banking_message ADD COLUMN timezone TEXT NOT NULL DEFAULT 'Europe/Tallinn';
ALTER TABLE banking_message ALTER COLUMN timezone DROP DEFAULT;
