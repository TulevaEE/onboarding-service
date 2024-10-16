package ee.tuleva.onboarding.deadline;

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
    return publicHolidays.nextWorkingDay(
        periodEnding().plusMonths(1).with(lastDayOfMonth()).toLocalDate());
  }

  public Instant getEarlyWithdrawalCancellationDeadline() {
    return earlyWithdrawalCancellationDeadline().toInstant();
  }

  private ZonedDateTime earlyWithdrawalCancellationDeadline() {
    return periodEnding().plusMonths(4).with(lastDayOfMonth());
  }

  public LocalDate getEarlyWithdrawalFulfillmentDate() {
    return publicHolidays.nextWorkingDay(
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
    return publicHolidays.nextWorkingDay(
        withdrawalCancellationDeadline().plusDays(15).toLocalDate());
  }

  public LocalDate getWithdrawalLatestFulfillmentDate() {
    return publicHolidays.nextWorkingDay(
        withdrawalCancellationDeadline().plusDays(20).toLocalDate());
  }

  public LocalDate getFulfillmentDate(ApplicationType applicationType) {
    return switch (applicationType) {
      case TRANSFER -> getTransferMandateFulfillmentDate();
      case WITHDRAWAL -> getWithdrawalFulfillmentDate();
      case EARLY_WITHDRAWAL -> getEarlyWithdrawalFulfillmentDate();
      case PAYMENT_RATE -> getPaymentRateFulfillmentDate();
      default -> throw new IllegalArgumentException("Unknown application type: " + applicationType);
    };
  }

  public Instant getCancellationDeadline(ApplicationType applicationType) {
    return switch (applicationType) {
      case TRANSFER -> getTransferMandateCancellationDeadline();
      case WITHDRAWAL -> getWithdrawalCancellationDeadline();
      case EARLY_WITHDRAWAL -> getEarlyWithdrawalCancellationDeadline();
      case PAYMENT_RATE -> getPaymentRateDeadline();
      default -> throw new IllegalArgumentException("Unknown application type: " + applicationType);
    };
  }

  public Instant getPaymentRateDeadline() {
    ZoneId timeZone = estonianClock.getZone();
    ZonedDateTime zonedApplicationDate = applicationDate.atZone(timeZone);
    int applicationYear = zonedApplicationDate.getYear();

    ZonedDateTime november30ApplicationYear =
        LocalDate.of(applicationYear, Month.NOVEMBER, 30)
            .atStartOfDay(timeZone)
            .with(LocalTime.MAX);

    if (zonedApplicationDate.isBefore(november30ApplicationYear)) {
      return november30ApplicationYear.toInstant();
    } else {
      return november30ApplicationYear.plusYears(1).toInstant();
    }
  }

  public LocalDate getPaymentRateFulfillmentDate() {
    ZoneId timeZone = estonianClock.getZone();
    ZonedDateTime zonedApplicationDate = applicationDate.atZone(timeZone);
    int applicationYear = zonedApplicationDate.getYear();

    ZonedDateTime november30ApplicationYear =
        LocalDate.of(applicationYear, Month.NOVEMBER, 30)
            .atStartOfDay(timeZone)
            .with(LocalTime.MAX);

    LocalDate startOfNextYear = LocalDate.of(applicationYear + 1, Month.JANUARY, 1);

    if (zonedApplicationDate.isBefore(november30ApplicationYear)) {
      return startOfNextYear;
    } else {
      return startOfNextYear.plusYears(1);
    }
  }
}
