-- Dev/test mirror of analytics.mv_crm. In production this is a materialized view owned by the
-- database-administration repo (refreshed via MaterializedViewRepository); here we create a
-- minimal table so dev/test can exercise the TKF new/existing-client classifier. IF NOT EXISTS
-- mirrors the existing analytics.* dev mirrors and yields to the real object where it exists.
CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE IF NOT EXISTS analytics.mv_crm
(
  personal_id             VARCHAR(255) PRIMARY KEY,
  balance_in_third_pillar BOOLEAN,
  balance_in_tuk75        BOOLEAN,
  balance_in_tuk00        BOOLEAN
);
