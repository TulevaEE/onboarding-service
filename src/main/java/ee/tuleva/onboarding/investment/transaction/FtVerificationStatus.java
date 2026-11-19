package ee.tuleva.onboarding.investment.transaction;

import org.jspecify.annotations.NullMarked;

@NullMarked
public enum FtVerificationStatus {
  OK,
  ERROR,
  PENDING_EXECUTION,
  PENDING_NAV,
  PRICE_MISSING,
  CANCELLED,
  AMBIGUOUS,
  IGNORED,
  ORPHAN,
  VOIDED,
  REPLACED,
  PENDING_REPLACEMENT,
  NOTHING_TO_CANCEL,
  UNCHECKED
}
