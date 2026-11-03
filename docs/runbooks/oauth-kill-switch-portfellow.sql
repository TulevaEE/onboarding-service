INSERT INTO oauth_client_suspension (client_id, suspended_at, reason)
SELECT 'portfellow', CURRENT_TIMESTAMP, 'kill switch'
WHERE NOT EXISTS (SELECT 1 FROM oauth_client_suspension WHERE client_id = 'portfellow');

UPDATE oauth_grant
SET revoked_at              = CURRENT_TIMESTAMP,
    revocation_reason       = 'KILL_SWITCH',
    authorization_code_hash = NULL,
    access_token_hash       = NULL,
    refresh_token_hash      = NULL
WHERE client_id = 'portfellow'
  AND revoked_at IS NULL;
