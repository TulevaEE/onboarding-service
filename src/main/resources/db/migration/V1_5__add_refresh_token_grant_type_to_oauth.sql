UPDATE oauth_client_details
SET authorized_grant_types = 'mobile_id,id_card,refresh_token'
WHERE client_id = 'onboarding-client';