INSERT INTO oauth2_registered_client(id, client_id, client_secret,
                                     client_secret_expires_at, client_name,
                                     client_authentication_methods, authorization_grant_types,
                                     redirect_uris, scopes, client_settings, token_settings)
SELECT client_id,
       client_id,
       client_secret,
       null,
       client_id,
       'client_secret_basic',
       COALESCE(authorized_grant_types, 'client_credentials'),
       web_server_redirect_uri,
       scope,
       '{"@class": "java.util.HashMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
       '{"@class": "java.util.HashMap", "settings.token.access-token-format": {"@class":"org.springframework.security.oauth2.core.OAuth2TokenFormat","value": "reference"}, "settings.token.refresh-token-time-to-live":["java.time.Duration",' ||
       COALESCE(refresh_token_validity, '3600') ||
       '.000000000],"settings.token.access-token-time-to-live":["java.time.Duration",' ||
       COALESCE(access_token_validity, '3600') || '.000000000]}'
FROM oauth_client_details;
