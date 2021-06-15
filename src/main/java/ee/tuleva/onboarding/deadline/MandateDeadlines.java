package ee.tuleva.onboarding.deadline;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.temporal.TemporalAdjusters.lastDayOfMonth;

import ee.tuleva.onboarding.mandate.application.ApplicationType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MandateDeadlines {

  private final Clock estonianClock;
  private final PublicHolidays publicHolidays;

  public Instant getPeriodEnding() {
    return periodEnding().toInstant();
  }

  private ZonedDateTime periodEnding() {
    ZonedDateTime march31ThisYear = now().withMonth(3).with(lastDayOfMonth()).with(LocalTime.MAX);
    ZonedDateTime july31ThisYear = now().withMonth(7).with(lastDayOfMonth()).with(LocalTime.MAX);
    ZonedDateTime november30ThisYear =
        now().withMonth(11).with(lastDayOfMonth()).with(LocalTime.MAX);

    return Stream.of(march31ThisYear, july31ThisYear, november30ThisYear)
        .filter(deadline -> !deadline.isBefore(now()))
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
    return now().with(lastDayOfMonth()).with(LocalTime.MAX);
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
