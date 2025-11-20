ALTER TABLE email ADD COLUMN mailchimp_campaign VARCHAR(255);

CREATE INDEX idx_emails_mailchimp_campaign ON email(mailchimp_campaign);
