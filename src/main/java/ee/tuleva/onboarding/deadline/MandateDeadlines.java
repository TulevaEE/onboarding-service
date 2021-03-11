package ee.tuleva.onboarding.deadline;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.temporal.TemporalAdjusters.lastDayOfMonth;

import java.time.Clock;
import java.time.LocalDate;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MandateDeadlines {

  private final Clock clock;
  private final PublicHolidays publicHolidays;

  public LocalDate periodEnding() {
    LocalDate march31ThisYear = now().withMonth(3).withDayOfMonth(31);
    LocalDate july31ThisYear = now().withMonth(7).withDayOfMonth(31);
    LocalDate november30ThisYear = now().withMonth(11).withDayOfMonth(30);

    return Stream.of(march31ThisYear, july31ThisYear, november30ThisYear)
        .filter(deadline -> !deadline.isBefore(now()))
        .findFirst()
        .get();
  }

  public LocalDate transferMandateCancellationDeadline() {
    return periodEnding();
  }

  public LocalDate transferMandateFulfillmentDate() {
    return nextWorkingDay(periodEnding().plusMonths(1).with(lastDayOfMonth()));
  }

  public LocalDate earlyWithdrawalCancellationDeadline() {
    return periodEnding().plusMonths(4).with(lastDayOfMonth());
  }

  public LocalDate earlyWithdrawalFulfillmentDate() {
    return nextWorkingDay(earlyWithdrawalCancellationDeadline().plusMonths(1).with(lastDayOfMonth()));
  }

  public LocalDate withdrawalCancellationDeadline() {
    return now().with(lastDayOfMonth());
  }

  public LocalDate withdrawalFulfillmentDate() {
    return nextWorkingDay(withdrawalCancellationDeadline().plusDays(15));
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

  private LocalDate now() {
    return LocalDate.now(clock);
  }
}
