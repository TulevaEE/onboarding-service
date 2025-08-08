ALTER TABLE public.exchange_transaction_snapshot
    ALTER COLUMN snapshot_taken_at TYPE TIMESTAMP 
    USING snapshot_taken_at AT TIME ZONE 'Europe/Tallinn';

ALTER TABLE public.exchange_transaction_snapshot
    ALTER COLUMN created_at TYPE TIMESTAMP 
    USING created_at AT TIME ZONE 'Europe/Tallinn';
