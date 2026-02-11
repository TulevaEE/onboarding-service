ALTER TABLE fund
  ALTER COLUMN pillar DROP NOT NULL;

INSERT INTO fund (fund_manager_id, isin, name_estonian, name_english, short_name, pillar,
                  management_fee_rate, equity_share, ongoing_charges_figure, status, inception_date)
SELECT (SELECT id FROM fund_manager WHERE name = 'Tuleva'),
       'EE0000003283',
       'Tuleva Täiendav Kogumisfond',
       'Tuleva Additional Investment Fund',
       'TKF100',
       NULL,
       0.0016,
       1.0,
       0.0029,
       'ACTIVE',
       '2025-09-18'
WHERE NOT EXISTS (SELECT 1 FROM fund WHERE isin = 'EE0000003283');

INSERT INTO index_values (key, date, value, provider, updated_at)
SELECT 'EE0000003283', '2026-02-01', 1.0000, 'MANUAL', NOW()
WHERE NOT EXISTS (SELECT 1 FROM index_values WHERE key = 'EE0000003283');
