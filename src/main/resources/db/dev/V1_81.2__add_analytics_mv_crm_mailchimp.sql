CREATE TABLE analytics.mv_crm_mailchimp
(
  isikukood CHAR(11) PRIMARY KEY,
  email     VARCHAR(255),
  keel      CHAR(3),
  vanus     INT
);
