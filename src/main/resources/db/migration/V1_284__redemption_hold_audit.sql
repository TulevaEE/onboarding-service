ALTER TABLE redemption_request ADD COLUMN hold_comment TEXT;
ALTER TABLE redemption_request ADD COLUMN hold_at TIMESTAMPTZ;
ALTER TABLE redemption_request ADD COLUMN held_by TEXT;
ALTER TABLE redemption_request ADD COLUMN hold_notified_at TIMESTAMPTZ;
ALTER TABLE redemption_request ADD COLUMN hold_released_at TIMESTAMPTZ;
ALTER TABLE redemption_request ADD COLUMN requeued_at TIMESTAMPTZ;

-- A redemption can be held for several reasons at once: a party can be both a PEP and high risk.
CREATE TABLE redemption_hold_reason (
    redemption_request_id UUID NOT NULL,
    reason VARCHAR(30) NOT NULL,
    CONSTRAINT pk_redemption_hold_reason PRIMARY KEY (redemption_request_id, reason),
    CONSTRAINT fk_redemption_hold_reason_request
        FOREIGN KEY (redemption_request_id) REFERENCES redemption_request (id)
);

-- SCREENING_MATCH no longer says whether the hit was a sanction or a PEP, and only a sanction may
-- stop an order. Existing rows are read as the stronger of the two so nothing is paid by accident.
INSERT INTO redemption_hold_reason (redemption_request_id, reason)
SELECT id, CASE WHEN hold_reason = 'SCREENING_MATCH' THEN 'SANCTION' ELSE hold_reason END
  FROM redemption_request
 WHERE hold_reason IS NOT NULL;

-- approve-review left hold_reason in place and recorded the decision in reviewed_at only. Without
-- this the migrated reason reads as an active hold again, so an approved payout would be held a
-- second time and an approved FAILED request could be neither retried nor released.
UPDATE redemption_request
   SET hold_released_at = reviewed_at
 WHERE reviewed_at IS NOT NULL
   AND hold_reason IS NOT NULL;

ALTER TABLE redemption_request DROP COLUMN hold_reason;

-- IN_REVIEW is retired. Its rows hold units that were never sold, so they can only become FROZEN:
-- PAYOUT_HELD would claim an execution that never happened. Releasing one requeues it and it is
-- priced at the next dealing date.
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
