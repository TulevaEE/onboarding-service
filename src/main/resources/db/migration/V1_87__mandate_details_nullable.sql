ALTER TABLE mandate ALTER COLUMN details DROP NOT NULL;

UPDATE mandate
SET details = null
WHERE details = '{}' AND mandate_type = 'UNKNOWN';

UPDATE mandate
SET mandate_type = 'WITHDRAWAL_CANCELLATION', details = '{"mandateType":"WITHDRAWAL_CANCELLATION"}'::jsonb
WHERE mandate_type = '0';

UPDATE mandate
SET mandate_type = 'EARLY_WITHDRAWAL_CANCELLATION', details = '{"mandateType":"EARLY_WITHDRAWAL_CANCELLATION"}'::jsonb
WHERE mandate_type = '1';

UPDATE mandate
SET mandate_type = 'TRANSFER_CANCELLATION', details = '{"mandateType":"TRANSFER_CANCELLATION"}'::jsonb
WHERE mandate_type = '2';
