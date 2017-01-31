INSERT INTO oauth_client_details(
            client_id, resource_ids, client_secret, scope, authorized_grant_types,
            web_server_redirect_uri, authorities, access_token_validity,
            refresh_token_validity, additional_information, autoapprove)

    SELECT 'onboarding-client', 'onboarding-service', 'onboarding-client', 'onboarding', 'mobile_id',
            null, null, 1800,
            null, null, null
    WHERE
      NOT EXISTS (
        SELECT * FROM oauth_client_details WHERE client_id = 'onboarding-client'
      );