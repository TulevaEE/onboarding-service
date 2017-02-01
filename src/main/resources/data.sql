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

INSERT INTO fund_manager(
            id, name)
    SELECT 1, 'Tuleva'
    WHERE
      NOT EXISTS (
        SELECT * FROM fund_manager WHERE name = 'Tuleva'
      );

INSERT INTO fund_manager(
            id, name)
    SELECT 2, 'LHV'
    WHERE
      NOT EXISTS (
        SELECT * FROM fund_manager WHERE name = 'LHV'
      );

INSERT INTO users(
            id, personal_code, first_name, last_name, created_date, member_number)
    SELECT 1, '39911223344', 'Tõnu', 'Pekk', '2015-01-31 14:06:01', 1
    WHERE
      NOT EXISTS (
        SELECT * FROM users WHERE id = 1
      );

INSERT INTO fund(
            id, isin, name, management_fee_percent, fund_manager)
    SELECT 1, 'AE123232334', 'Aktsia', 0.35, 1
    WHERE
      NOT EXISTS (
        SELECT * FROM fund WHERE id = 1
      );

INSERT INTO fund(
            id, isin, name, management_fee_percent, fund_manager)
    SELECT 2, 'AE123232335', 'Võlakirjad', 0.35, 1
    WHERE
      NOT EXISTS (
        SELECT * FROM fund WHERE id = 1
      );

INSERT INTO fund(
            id, isin, name, management_fee_percent, fund_manager)
    SELECT 3, 'AE123232337', 'LHV XL', 0.95, 2
    WHERE
      NOT EXISTS (
        SELECT * FROM fund WHERE id = 3
      );