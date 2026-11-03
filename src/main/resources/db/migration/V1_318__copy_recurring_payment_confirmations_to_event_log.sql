INSERT INTO event_log (timestamp, principal, type, data)
SELECT CAST(confirmed_at AS TIMESTAMP), personal_code, 'RECURRING_PAYMENT_CONFIRMED', NULL
FROM third_pillar_recurring_payment_confirmation;
