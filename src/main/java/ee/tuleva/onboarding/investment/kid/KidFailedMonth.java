package ee.tuleva.onboarding.investment.kid;

import java.time.YearMonth;

record KidFailedMonth(YearMonth month, String failedWith) {

  static KidFailedMonth of(YearMonth month, RuntimeException cause) {
    return new KidFailedMonth(month, cause.getClass().getSimpleName());
  }
}
