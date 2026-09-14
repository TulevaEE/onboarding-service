package ee.tuleva.onboarding.banking.statement;

import java.time.LocalDate;

public record StatementPeriod(LocalDate from, LocalDate to) {

  public boolean covers(LocalDate date) {
    return !date.isBefore(from) && !date.isAfter(to);
  }
}
