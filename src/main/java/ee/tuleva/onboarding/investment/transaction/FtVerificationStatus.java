package ee.tuleva.onboarding.investment.transaction;

import org.jspecify.annotations.NullMarked;

@NullMarked
public enum FtVerificationStatus {
  OK,
  ERROR,
  PENDING_EXECUTION,
  PENDING_NAV,
  CANCELLED,
  AMBIGUOUS,
  IGNORED,
  ORPHAN
}
