package ee.tuleva.onboarding.investment.check.tracking;

import java.time.LocalDate;

record GapWindow(LocalDate today, int lookbackDays) {

  LocalDate from() {
    return today.minusDays(lookbackDays);
  }
}
