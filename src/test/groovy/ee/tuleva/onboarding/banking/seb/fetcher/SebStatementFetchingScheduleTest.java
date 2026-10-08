package ee.tuleva.onboarding.banking.seb.fetcher;

import static ee.tuleva.onboarding.banking.seb.fetcher.SebStatementFetchingScheduler.CURRENT_DAY_FETCH_BEFORE_SUBSCRIPTION_CUTOFF_CRON;
import static ee.tuleva.onboarding.banking.seb.fetcher.SebStatementFetchingScheduler.CURRENT_DAY_FETCH_CRON;
import static ee.tuleva.onboarding.banking.seb.fetcher.SebStatementFetchingScheduler.CURRENT_DAY_FETCH_IN_THE_HOUR_BEFORE_SUBSCRIPTION_CUTOFF_CRON;
import static ee.tuleva.onboarding.banking.seb.fetcher.SebStatementFetchingScheduler.CURRENT_DAY_FETCH_WHILE_PAYMENTS_AWAIT_APPROVAL_CRON;
import static ee.tuleva.onboarding.banking.seb.fetcher.SebStatementFetchingScheduler.END_OF_DAY_FETCH_CRON;
import static ee.tuleva.onboarding.banking.seb.fetcher.SebStatementFetchingScheduler.GAP_REPORT_CRON;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.banking.payment.PaymentApprovalReminderJob;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

class SebStatementFetchingScheduleTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  @Test
  void currentDayFetch_firesEveryThirtyMinutesOnAWorkingDayUntilTheHourBeforeTheCutoff() {
    var fires = firesOn("2026-07-24", CURRENT_DAY_FETCH_CRON);

    assertThat(fires).hasSize(12);
    assertThat(fires.getFirst().toLocalTime()).hasToString("09:00");
    assertThat(fires.getLast().toLocalTime()).hasToString("14:30");
    assertThat(fires).allSatisfy(fire -> assertThat(fire.getMinute()).isIn(0, 30));
  }

  @Test
  void currentDayFetchWhilePaymentsAwaitApproval_firesEveryFiveMinutesFromAfterTheCutoffUntilSix() {
    var fires = firesOn("2026-07-24", CURRENT_DAY_FETCH_WHILE_PAYMENTS_AWAIT_APPROVAL_CRON);

    assertThat(fires).hasSize(24);
    assertThat(fires.getFirst().toLocalTime()).hasToString("16:03");
    assertThat(fires.getLast().toLocalTime()).hasToString("17:58");
    for (int i = 1; i < fires.size(); i++) {
      assertThat(Duration.between(fires.get(i - 1), fires.get(i))).isEqualTo(Duration.ofMinutes(5));
    }
  }

  @Test
  void
      currentDayFetchWhilePaymentsAwaitApproval_landsTwoMinutesBeforeEachApprovalReminderSoTheReminderReadsAProcessedStatement() {
    var fetches = firesOn("2026-07-24", CURRENT_DAY_FETCH_WHILE_PAYMENTS_AWAIT_APPROVAL_CRON);
    var reminders =
        Stream.of(
                PaymentApprovalReminderJob.FROM_TWENTY_PAST_FOUR_CRON,
                PaymentApprovalReminderJob.UNTIL_SIX_CRON)
            .flatMap(cron -> firesOn("2026-07-24", cron).stream())
            .toList();

    assertThat(reminders)
        .isNotEmpty()
        .allSatisfy(reminder -> assertThat(fetches).contains(reminder.minusMinutes(2)));
  }

  @Test
  void currentDayFetchInTheHourBeforeCutoff_firesEveryFiveMinutesFromThreeUntilFiveToFour() {
    var fires =
        firesOn("2026-07-24", CURRENT_DAY_FETCH_IN_THE_HOUR_BEFORE_SUBSCRIPTION_CUTOFF_CRON);

    assertThat(fires).hasSize(12);
    assertThat(fires.getFirst().toLocalTime()).hasToString("15:00");
    assertThat(fires.getLast().toLocalTime()).hasToString("15:55");
    for (int i = 1; i < fires.size(); i++) {
      assertThat(Duration.between(fires.get(i - 1), fires.get(i))).isEqualTo(Duration.ofMinutes(5));
    }
  }

  @Test
  void currentDayFetches_leaveAtMostFiveMinutesUncoveredIfTheFetchBeforeTheCutoffFails() {
    var fetchBeforeCutoff =
        firesOn("2026-07-24", CURRENT_DAY_FETCH_BEFORE_SUBSCRIPTION_CUTOFF_CRON).getFirst();

    var lastEarlierFetch =
        Stream.of(
                CURRENT_DAY_FETCH_CRON,
                CURRENT_DAY_FETCH_IN_THE_HOUR_BEFORE_SUBSCRIPTION_CUTOFF_CRON)
            .flatMap(cron -> firesOn("2026-07-24", cron).stream())
            .filter(fire -> fire.isBefore(fetchBeforeCutoff))
            .max(Comparator.naturalOrder())
            .orElseThrow();

    assertThat(lastEarlierFetch.toLocalTime()).hasToString("15:55");
  }

  @Test
  void
      currentDayFetchBeforeSubscriptionCutoff_firesOnceThirtySecondsBeforeFourSoSebStampsItsReportBeforeTheCutoff() {
    var fires = firesOn("2026-07-24", CURRENT_DAY_FETCH_BEFORE_SUBSCRIPTION_CUTOFF_CRON);

    assertThat(fires).hasSize(1);
    assertThat(fires.getFirst().toLocalTime()).hasToString("15:59:30");
  }

  @Test
  void currentDayFetches_doNotFireAtTheWeekend() {
    assertThat(firesOn("2026-07-25", CURRENT_DAY_FETCH_CRON)).isEmpty();
    assertThat(firesOn("2026-07-25", CURRENT_DAY_FETCH_IN_THE_HOUR_BEFORE_SUBSCRIPTION_CUTOFF_CRON))
        .isEmpty();
    assertThat(firesOn("2026-07-25", CURRENT_DAY_FETCH_BEFORE_SUBSCRIPTION_CUTOFF_CRON)).isEmpty();
    assertThat(firesOn("2026-07-25", CURRENT_DAY_FETCH_WHILE_PAYMENTS_AWAIT_APPROVAL_CRON))
        .isEmpty();
  }

  @Test
  void endOfDayFetch_firesEveryThirtyMinutesFromFourUntilHalfPastEleven() {
    var fires = firesOn("2026-09-12", END_OF_DAY_FETCH_CRON);

    assertThat(fires).hasSize(40);
    assertThat(fires.getFirst().toLocalTime()).hasToString("04:00");
    assertThat(fires.getLast().toLocalTime()).hasToString("23:30");
    for (int i = 1; i < fires.size(); i++) {
      assertThat(Duration.between(fires.get(i - 1), fires.get(i)))
          .isEqualTo(Duration.ofMinutes(30));
    }
  }

  @Test
  void endOfDayFetch_keepsGoingAfterTheOldSixOClockAttemptThatStrandedFridaysStatements() {
    var lastOldAttempt = LocalDateTime.parse("2026-09-12T06:00:00").atZone(TALLINN);

    var nextFire = CronExpression.parse(END_OF_DAY_FETCH_CRON).next(lastOldAttempt);

    assertThat(nextFire).isEqualTo(LocalDateTime.parse("2026-09-12T06:30:00").atZone(TALLINN));
  }

  @Test
  void gapReport_firesOnceADayAfterTheNineOClockFetchHasBeenProcessed() {
    var fires = firesOn("2026-09-12", GAP_REPORT_CRON);

    assertThat(fires).hasSize(1);
    assertThat(fires.getFirst().toLocalTime()).hasToString("09:10");
  }

  private static List<ZonedDateTime> firesOn(String date, String cronExpression) {
    var cron = CronExpression.parse(cronExpression);
    var cursor = LocalDateTime.parse(date + "T00:00:00").atZone(TALLINN);
    var endOfDay = cursor.plusDays(1);
    var fires = new ArrayList<ZonedDateTime>();
    while (true) {
      var next = cron.next(cursor);
      if (next == null || !next.isBefore(endOfDay)) {
        return fires;
      }
      fires.add(next);
      cursor = next;
    }
  }
}
