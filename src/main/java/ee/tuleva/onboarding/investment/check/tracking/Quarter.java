package ee.tuleva.onboarding.investment.check.tracking;

import java.time.LocalDate;

record Quarter(int year, int number) {

  Quarter {
    if (number < 1 || number > 4) {
      throw new IllegalArgumentException(
          "Quarter must be 1-4: year=" + year + ", number=" + number);
    }
  }

  LocalDate start() {
    return LocalDate.of(year, (number - 1) * 3 + 1, 1);
  }

  LocalDate end() {
    return start().plusMonths(3).minusDays(1);
  }
}
