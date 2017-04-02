INSERT INTO oauth_client_details(
            client_id, resource_ids, client_secret, scope, authorized_grant_types,
            web_server_redirect_uri, authorities, access_token_validity,
            refresh_token_validity, additional_information, autoapprove)

    SELECT 'onboarding-client', 'onboarding-service', 'onboarding-client', 'onboarding', 'mobile_id, id_card',
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

INSERT INTO fund_manager(
            id, name)
    SELECT 3, 'Swedbank'
    WHERE
      NOT EXISTS (
        SELECT * FROM fund_manager WHERE name = 'Swedbank'
      );
INSERT INTO fund_manager(
            id, name)
    SELECT 4, 'Nordea'
    WHERE
      NOT EXISTS (
        SELECT * FROM fund_manager WHERE name = 'Nordea'
      );

INSERT INTO fund_manager(
            id, name)
    SELECT 5, 'SEB'
    WHERE
      NOT EXISTS (
        SELECT * FROM fund_manager WHERE name = 'SEB'
      );

INSERT INTO fund(
            isin, name, management_fee_rate, fund_manager_id)
    SELECT 'EE3600109435', 'Aktsia', 0.0035, 1
    WHERE
      NOT EXISTS (
        SELECT * FROM fund WHERE id = 1
      );

INSERT INTO fund(
            isin, name, management_fee_rate, fund_manager_id)
    SELECT 'EE3600109443', 'Võlakirjad', 0.0035, 1
    WHERE
      NOT EXISTS (
        SELECT * FROM fund WHERE id = 2
      );

INSERT INTO fund(
            isin, name, management_fee_rate, fund_manager_id)
    SELECT 'AE123232337', 'LHV XL', 0.0095, 2
    WHERE
      NOT EXISTS (
        SELECT * FROM fund WHERE id = 3
      );

INSERT INTO fund(
            isin, name, management_fee_rate, fund_manager_id)
    SELECT 'EE3600019790', 'Kohustuslik Pensionifond Danske Pension 25', 0.96425, 2
    WHERE
      NOT EXISTS (
        SELECT * FROM fund WHERE id = 4
      );

INSERT INTO fund(
            isin, name, management_fee_rate, fund_manager_id)
    SELECT 'EE3600019774', 'LHV Pensionifond M', 1.06400, 2
    WHERE
      NOT EXISTS (
        SELECT * FROM fund WHERE id = 5
      );



INSERT INTO users(
            id, active, personal_code, first_name, last_name, created_date, updated_date, member_number, phone_number, email)
    SELECT 0, true, '38812022762', 'Jordan', 'Valdma', '2015-01-31 14:06:01', '2017-01-31 14:06:01', 100, '5523533', 'first.last@mail.ee'
    WHERE
      NOT EXISTS (
        SELECT * FROM users WHERE id = 0
      );

INSERT INTO users(
            id, active, personal_code, first_name, last_name, created_date, updated_date, member_number, phone_number, email)
    SELECT 1, true, '39911223344', 'Firstname', 'Lastname', '2015-01-31 14:06:01', '2017-01-31 14:06:01', 1, '1234567', 'first.last@mail.ee'
    WHERE
      NOT EXISTS (
        SELECT * FROM users WHERE id = 1
      );

INSERT INTO users(
            id, active, personal_code, first_name, last_name, created_date, updated_date, member_number, phone_number, email)
    SELECT 2, true, '37807256017', 'Ziim', 'Kaba', '2015-01-31 14:06:01', '2017-01-31 14:06:01', 2419, '1234567', 'first.last@mail.ee'
    WHERE
      NOT EXISTS (
        SELECT * FROM users WHERE id = 2
      );



INSERT INTO initial_capital(
            id, user_id, amount, currency)
    SELECT 1, 1, 10000.00, 'EUR'
    WHERE
      NOT EXISTS (
        SELECT * FROM initial_capital WHERE id = 1
      );