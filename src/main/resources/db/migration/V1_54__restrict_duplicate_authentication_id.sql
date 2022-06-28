DELETE FROM oauth_access_token WHERE authentication_id IN
                                     (SELECT authentication_id
                                      FROM oauth_access_token
                                      GROUP BY authentication_id
                                      HAVING count(*) > 1);

ALTER TABLE oauth_access_token ADD CONSTRAINT unique_authentication_id UNIQUE (authentication_id);