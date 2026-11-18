package ee.tuleva.onboarding.fund.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class PensionFundStatisticsScheduledUpdateTest {

  private final PensionFundStatisticsService pensionFundStatisticsService =
      mock(PensionFundStatisticsService.class);
  private final PensionFundStatisticsScheduledUpdate scheduledUpdate =
      new PensionFundStatisticsScheduledUpdate(pensionFundStatisticsService);

  @Test
  void refreshesAtTheTopOfEveryWeekdayHour() throws NoSuchMethodException {
    Scheduled scheduled = scheduledMethod("refresh").getAnnotation(Scheduled.class);

    assertThat(scheduled.cron()).isEqualTo("0 0 * * * MON-FRI");
  }

  @Test
  void refreshesAgainAt1617TallinnTimeSoTheThirdPillarNavSebEntersBy1615IsOnTheFundPageBy1630()
      throws NoSuchMethodException {
    Scheduled scheduled =
        scheduledMethod("refreshAfterThirdPillarNavDeadline").getAnnotation(Scheduled.class);

    assertThat(scheduled.cron()).isEqualTo("0 17 16 * * MON-FRI");
    assertThat(scheduled.zone()).isEqualTo("Europe/Tallinn");
  }

  @Test
  void theAfternoonRefreshHasItsOwnShortLock() throws NoSuchMethodException {
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

    verify(pensionFundStatisticsService).refreshCachedStatistics();
  }

  @Test
  void afternoonRefreshUpdatesTheCachedStatistics() {
    scheduledUpdate.refreshAfterThirdPillarNavDeadline();

    verify(pensionFundStatisticsService).refreshCachedStatistics();
  }

  private static Method scheduledMethod(String name) throws NoSuchMethodException {
    return PensionFundStatisticsScheduledUpdate.class.getMethod(name);
  }
}
