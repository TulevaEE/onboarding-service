ALTER TABLE nav_report ADD COLUMN sent_to_trustee_at timestamptz;

UPDATE nav_report SET sent_to_trustee_at = published_at WHERE published_at IS NOT NULL;
