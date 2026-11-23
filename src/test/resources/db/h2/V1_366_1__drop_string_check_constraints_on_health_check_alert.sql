ALTER TABLE investment_health_check_alert
    DROP CONSTRAINT IF EXISTS investment_health_check_alert_status;
ALTER TABLE investment_health_check_alert
    DROP CONSTRAINT IF EXISTS investment_health_check_alert_posted_carries_its_time;
