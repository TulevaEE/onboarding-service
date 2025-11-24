package ee.tuleva.onboarding.fund.statistics;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Profile({"!dev & !staging"})
@Component
public class PensionFundStatisticsScheduledUpdate {

  private final PensionFundStatisticsService pensionFundStatisticsService;

  @Scheduled(cron = "0 0 * * * MON-FRI")
  @SchedulerLock(
      name = "PensionFundStatisticsScheduledUpdate_refresh",
      lockAtMostFor = "55m",
      lockAtLeastFor = "5m")
  public void refresh() {
    pensionFundStatisticsService.refreshCachedStatistics();
  }
}
