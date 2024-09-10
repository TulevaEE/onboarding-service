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
SET mandate_type = 'TRANSFER_CANCELLATION',
    details = jsonb_set(
        jsonb_set(
            '{"mandateType":"TRANSFER_CANCELLATION"}'::jsonb,
            '{pillar}',
            to_jsonb((CASE WHEN mandate.pillar = 2 THEN 'SECOND' ELSE 'THIRD' END)::text)
        ),
        '{sourceFundIsinOfTransferToCancel}',
        to_jsonb(fund_transfer_exchange.source_fund_isin)
    )
  FROM fund_transfer_exchange
WHERE mandate_type = '2'
  AND fund_transfer_exchange.mandate_id = mandate.id
  AND fund_transfer_exchange.target_fund_isin IS NULL
  AND fund_transfer_exchange.amount IS NULL;
