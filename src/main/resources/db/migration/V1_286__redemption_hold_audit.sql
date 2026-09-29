ALTER TABLE redemption_request ADD COLUMN hold_comment TEXT;
ALTER TABLE redemption_request ADD COLUMN hold_at TIMESTAMPTZ;
ALTER TABLE redemption_request ADD COLUMN held_by TEXT;
ALTER TABLE redemption_request ADD COLUMN hold_notified_at TIMESTAMPTZ;
ALTER TABLE redemption_request ADD COLUMN hold_released_at TIMESTAMPTZ;
ALTER TABLE redemption_request ADD COLUMN requeued_at TIMESTAMPTZ;

CREATE TABLE redemption_hold_reason (
    redemption_request_id UUID NOT NULL,
    reason VARCHAR(30) NOT NULL,
    CONSTRAINT pk_redemption_hold_reason PRIMARY KEY (redemption_request_id, reason),
    CONSTRAINT fk_redemption_hold_reason_request
        FOREIGN KEY (redemption_request_id) REFERENCES redemption_request (id)
);

INSERT INTO redemption_hold_reason (redemption_request_id, reason)
SELECT id, CASE WHEN hold_reason = 'SCREENING_MATCH' THEN 'SANCTION' ELSE hold_reason END
  FROM redemption_request
 WHERE hold_reason IS NOT NULL;

UPDATE redemption_request
   SET hold_released_at = reviewed_at,
       requeued_at = reviewed_at
 WHERE reviewed_at IS NOT NULL
   AND hold_reason IS NOT NULL
   AND status = 'VERIFIED'
   AND cash_amount IS NULL;

UPDATE redemption_request
   SET hold_released_at = reviewed_at
 WHERE reviewed_at IS NOT NULL
   AND hold_reason IS NOT NULL
   AND hold_released_at IS NULL;

ALTER TABLE redemption_request DROP COLUMN hold_reason;

UPDATE redemption_request
   SET status = 'FROZEN',
       hold_at = updated_at,
       held_by = 'MIGRATION'
 WHERE status = 'IN_REVIEW';

INSERT INTO redemption_hold_reason (redemption_request_id, reason)
SELECT id, 'SANCTION'
  FROM redemption_request r
 WHERE r.status = 'FROZEN'
   AND NOT EXISTS (SELECT 1 FROM redemption_hold_reason h WHERE h.redemption_request_id = r.id);
