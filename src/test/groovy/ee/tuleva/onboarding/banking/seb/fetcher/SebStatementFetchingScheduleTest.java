package ee.tuleva.onboarding.banking.seb.fetcher;

import static ee.tuleva.onboarding.banking.seb.fetcher.SebStatementFetchingScheduler.END_OF_DAY_FETCH_CRON;
import static ee.tuleva.onboarding.banking.seb.fetcher.SebStatementFetchingScheduler.GAP_REPORT_CRON;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

class SebStatementFetchingScheduleTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

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
  void gapReport_firesOnceADayAtNine() {
    var fires = firesOn("2026-09-12", GAP_REPORT_CRON);

    assertThat(fires).hasSize(1);
    assertThat(fires.getFirst().toLocalTime()).hasToString("09:00");
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
