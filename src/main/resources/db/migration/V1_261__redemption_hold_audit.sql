ALTER TABLE redemption_request ADD COLUMN hold_reason TEXT;
ALTER TABLE redemption_request ADD COLUMN hold_at TIMESTAMPTZ;
ALTER TABLE redemption_request ADD COLUMN held_by TEXT;
ALTER TABLE redemption_request ADD COLUMN hold_notified_at TIMESTAMPTZ;

-- IN_REVIEW is retired: a sanctions hit now freezes the order (FROZEN) and any other AML suspicion
-- lets the order execute but holds the payout (PAYOUT_HELD). Production had no IN_REVIEW rows when
-- this was written; any that do exist land in the safe, non-executing state.
UPDATE redemption_request
   SET status = 'FROZEN',
       hold_reason = 'LEGACY_IN_REVIEW',
       hold_at = updated_at,
       held_by = 'MIGRATION'
 WHERE status = 'IN_REVIEW';
