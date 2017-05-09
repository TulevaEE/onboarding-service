UPDATE oauth_client_details
SET authorized_grant_types = 'mobile_id,id_card,refresh_token',
  access_token_validity = 3600,
  refresh_token_validity = 3600
WHERE client_id = 'onboarding-client';