package ee.tuleva.onboarding.investment.check.limit;

import java.util.EnumSet;
import java.util.Set;

public enum CheckType {
  POSITION,
  PROVIDER,
  RESERVE,
  FREE_CASH,
  OWNERSHIP;

  static final Set<CheckType> WRITTEN_BY_THE_DAILY_CHECK =
      EnumSet.of(POSITION, PROVIDER, RESERVE, FREE_CASH);
}
