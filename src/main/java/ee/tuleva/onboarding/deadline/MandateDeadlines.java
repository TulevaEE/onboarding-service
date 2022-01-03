package ee.tuleva.onboarding.deadline;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.temporal.TemporalAdjusters.lastDayOfMonth;

import ee.tuleva.onboarding.mandate.application.ApplicationType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MandateDeadlines {

  private final Clock estonianClock;
  private final PublicHolidays publicHolidays;
  private final Instant applicationDate;

  public Instant getPeriodEnding() {
    return periodEnding().toInstant();
  }

  private ZonedDateTime periodEnding() {
    ZoneId timeZone = estonianClock.getZone();
    ZonedDateTime zonedApplicationDate = applicationDate.atZone(timeZone);
    int applicationYear = zonedApplicationDate.getYear();

    ZonedDateTime march31 =
        LocalDate.of(applicationYear, Month.MARCH, 31).atStartOfDay(timeZone).with(LocalTime.MAX);
    ZonedDateTime july31 =
        LocalDate.of(applicationYear, Month.JULY, 31).atStartOfDay(timeZone).with(LocalTime.MAX);
    ZonedDateTime november30 =
        LocalDate.of(applicationYear, Month.NOVEMBER, 30)
            .atStartOfDay(timeZone)
            .with(LocalTime.MAX);
    ZonedDateTime march31NextYear = march31.plusYears(1);

    return Stream.of(march31, july31, november30, march31NextYear)
        .filter(deadline -> !deadline.isBefore(zonedApplicationDate))
        .findFirst()
        .get();
  }

  public Instant getTransferMandateCancellationDeadline() {
    return getPeriodEnding();
  }

  public LocalDate getTransferMandateFulfillmentDate() {
    return nextWorkingDay(periodEnding().plusMonths(1).with(lastDayOfMonth()).toLocalDate());
  }

  public Instant getEarlyWithdrawalCancellationDeadline() {
    return earlyWithdrawalCancellationDeadline().toInstant();
  }

  private ZonedDateTime earlyWithdrawalCancellationDeadline() {
    return periodEnding().plusMonths(4).with(lastDayOfMonth());
  }

  public LocalDate getEarlyWithdrawalFulfillmentDate() {
    return nextWorkingDay(
        earlyWithdrawalCancellationDeadline().plusMonths(1).with(lastDayOfMonth()).toLocalDate());
  }

  public Instant getWithdrawalCancellationDeadline() {
    return withdrawalCancellationDeadline().toInstant();
  }

  private ZonedDateTime withdrawalCancellationDeadline() {
    return applicationDate
        .atZone(estonianClock.getZone())
        .with(lastDayOfMonth())
        .with(LocalTime.MAX);
  }

  public LocalDate getWithdrawalFulfillmentDate() {
    return nextWorkingDay(withdrawalCancellationDeadline().plusDays(15).toLocalDate());
  }

  public LocalDate getFulfillmentDate(ApplicationType applicationType) {
    return switch (applicationType) {
      case TRANSFER -> getTransferMandateFulfillmentDate();
      case WITHDRAWAL -> getWithdrawalFulfillmentDate();
      case EARLY_WITHDRAWAL -> getEarlyWithdrawalFulfillmentDate();
      default -> throw new IllegalArgumentException("Unknown application type: " + applicationType);
    };
  }

  public Instant getCancellationDeadline(ApplicationType applicationType) {
    return switch (applicationType) {
      case TRANSFER -> getTransferMandateCancellationDeadline();
      case WITHDRAWAL -> getWithdrawalCancellationDeadline();
      case EARLY_WITHDRAWAL -> getEarlyWithdrawalCancellationDeadline();
      default -> throw new IllegalArgumentException("Unknown application type: " + applicationType);
    };
  }

  private LocalDate nextWorkingDay(LocalDate to) {
    LocalDate date = to.plusDays(1);
    while (!isWorkingDay(date)) {
      date = date.plusDays(1);
    }
    return date;
  }

  private boolean isWorkingDay(LocalDate date) {
    return !(date.getDayOfWeek() == SATURDAY
        || date.getDayOfWeek() == SUNDAY
        || publicHolidays.isPublicHoliday(date));
  }

  private ZonedDateTime now() {
    return ZonedDateTime.now(estonianClock);
  }
}
