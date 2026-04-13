package ee.tuleva.onboarding.investment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

class JobRunScheduleTest {

  private static final ZoneId TALLINN = ZoneId.of(JobRunSchedule.TIMEZONE);

  @Test
  void importBusinessHours_firesEveryFiveMinutesFromEightThroughSeventeen() {
    CronExpression cron = CronExpression.parse(JobRunSchedule.IMPORT_BUSINESS_HOURS);

    ZonedDateTime cursor = LocalDateTime.parse("2026-04-13T00:00:00").atZone(TALLINN);
    ZonedDateTime endOfDay = cursor.plusDays(1);
    List<ZonedDateTime> fires = new ArrayList<>();
    while (true) {
      ZonedDateTime next = cron.next(cursor);
      if (next == null || !next.isBefore(endOfDay)) break;
      fires.add(next);
      cursor = next;
    }

    // 10 hours (8 through 17 inclusive) * 12 fires/hour = 120 fires
    assertThat(fires).hasSize(120);
    assertThat(fires.get(0).getHour()).isEqualTo(8);
    assertThat(fires.get(0).getMinute()).isEqualTo(0);
    assertThat(fires.get(fires.size() - 1).getHour()).isEqualTo(17);
    assertThat(fires.get(fires.size() - 1).getMinute()).isEqualTo(55);
    for (int i = 1; i < fires.size(); i++) {
      assertThat(Duration.between(fires.get(i - 1), fires.get(i)).toMinutes()).isEqualTo(5);
    }
  }

  @Test
  void importBusinessHours_coversWhatTheOldScheduleMissed() {
    // The 2026-04-10 _uuendatud incident: SEB sent the corrected file at 13:29 Tallinn. Under
    // the previous schedule (5-min ticks 08:00-11:55, then ONE shot at 15:00) the next automatic
    // re-fetch after 15:00 was 08:00 the next morning — a ~17-hour gap. Pin that the new schedule
    // would have caught it: there must be at least one fire between 13:29 and 16:00 the same day.
    CronExpression cron = CronExpression.parse(JobRunSchedule.IMPORT_BUSINESS_HOURS);

    ZonedDateTime incidentMoment = LocalDateTime.parse("2026-04-10T13:29:00").atZone(TALLINN);
    ZonedDateTime cutoff = LocalDateTime.parse("2026-04-10T16:00:00").atZone(TALLINN);
    ZonedDateTime nextFire = cron.next(incidentMoment);

    assertThat(nextFire).isNotNull();
    assertThat(nextFire).isBefore(cutoff);
  }
}
