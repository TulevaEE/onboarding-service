package ee.tuleva.onboarding.investment.check.limit;

import java.util.EnumSet;
import java.util.Set;

public enum CheckType {
  POSITION,
  // No longer written: the issuer-level limit check was removed. Kept so the stored rows, which
  // are control evidence, stay readable.
  PROVIDER,
  RESERVE,
  FREE_CASH,
  OWNERSHIP;

  static final Set<CheckType> WRITTEN_BY_THE_DAILY_CHECK = EnumSet.of(POSITION, RESERVE, FREE_CASH);
}
