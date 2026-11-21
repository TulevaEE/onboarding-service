package ee.tuleva.onboarding.investment.transaction;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public record TradePrepRunExecution(
    @Nullable Instant executionTimestamp, @Nullable LocalDate reportedDate) {

  public Optional<LocalDate> tradeDateOrTheDayTheCustodianFirstReportedIt(ZoneId zone) {
    return executionTimestamp != null
        ? Optional.of(executionTimestamp.atZone(zone).toLocalDate())
        : Optional.ofNullable(reportedDate);
  }
}
