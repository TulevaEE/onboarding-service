package ee.tuleva.onboarding.fund.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

class PensionFundStatisticsScheduledUpdateTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final PensionFundStatisticsService pensionFundStatisticsService =
      mock(PensionFundStatisticsService.class);
  private final PensionFundStatisticsScheduledUpdate scheduledUpdate =
      new PensionFundStatisticsScheduledUpdate(pensionFundStatisticsService);

  @Test
  void hourlyRefreshRunsAtTheTopOfTheNextWeekdayHour() throws NoSuchMethodException {
    assertThat(nextRun("refresh", tallinn(2026, 11, 18, 10, 5)))
        .isEqualTo(tallinn(2026, 11, 18, 11, 0));
    assertThat(nextRun("refresh", tallinn(2026, 11, 21, 10, 5)))
        .isEqualTo(tallinn(2026, 11, 23, 0, 0));
  }

  @Test
  void afternoonRefreshRunsAt1617TallinnTimeSoTheThirdPillarNavEnteredBy1615IsOnTheFundPageBy1630()
      throws NoSuchMethodException {
    assertThat(nextRun("refreshAfterThirdPillarNavDeadline", tallinn(2026, 11, 18, 16, 15)))
        .isEqualTo(tallinn(2026, 11, 18, 16, 17));
  }

  @Test
  void afternoonRefreshStaysAt1617TallinnTimeInSummer() throws NoSuchMethodException {
    assertThat(nextRun("refreshAfterThirdPillarNavDeadline", tallinn(2026, 7, 15, 16, 15)))
        .isEqualTo(tallinn(2026, 7, 15, 16, 17));
  }

  @Test
  void afternoonRefreshSkipsTheWeekend() throws NoSuchMethodException {
    assertThat(nextRun("refreshAfterThirdPillarNavDeadline", tallinn(2026, 11, 20, 16, 18)))
        .isEqualTo(tallinn(2026, 11, 23, 16, 17));
  }

  @Test
  void afternoonRefreshHasItsOwnShortLock() throws NoSuchMethodException {
    SchedulerLock hourly = scheduledMethod("refresh").getAnnotation(SchedulerLock.class);
    SchedulerLock afternoon =
        scheduledMethod("refreshAfterThirdPillarNavDeadline").getAnnotation(SchedulerLock.class);

    assertThat(afternoon.name()).isNotEqualTo(hourly.name());
    assertThat(afternoon.lockAtMostFor()).isEqualTo("10m");
    assertThat(afternoon.lockAtLeastFor()).isEqualTo("1m");
  }

  @Test
  void hourlyRefreshUpdatesTheCachedStatistics() {
    scheduledUpdate.refresh();

    then(pensionFundStatisticsService).should().refreshCachedStatistics();
  }

  @Test
  void afternoonRefreshUpdatesTheCachedStatistics() {
    scheduledUpdate.refreshAfterThirdPillarNavDeadline();

    then(pensionFundStatisticsService).should().refreshCachedStatistics();
  }

  private static ZonedDateTime nextRun(String methodName, ZonedDateTime after)
      throws NoSuchMethodException {
    Scheduled scheduled = scheduledMethod(methodName).getAnnotation(Scheduled.class);
    ZoneId zone = scheduled.zone().isEmpty() ? after.getZone() : ZoneId.of(scheduled.zone());
    return CronExpression.parse(scheduled.cron())
        .next(after.withZoneSameInstant(zone))
        .withZoneSameInstant(TALLINN);
  }

  private static ZonedDateTime tallinn(int year, int month, int day, int hour, int minute) {
    return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, TALLINN);
  }

  private static Method scheduledMethod(String name) throws NoSuchMethodException {
    return PensionFundStatisticsScheduledUpdate.class.getMethod(name);
  }
}
