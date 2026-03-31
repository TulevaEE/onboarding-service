CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE IF NOT EXISTS analytics.v_tkf_risk_metadata (
    personal_id VARCHAR(50) PRIMARY KEY,
    risk_level INT,
    metadata JSON
);
