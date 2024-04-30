package ee.tuleva.onboarding.fund.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Profile({"!dev & !staging"})
@Component
public class PensionFundStatisticsScheduledUpdate {

  private final PensionFundStatisticsService pensionFundStatisticsService;

  @Scheduled(cron = "0 0 * * * MON-FRI")
  public void refresh() {
    pensionFundStatisticsService.refreshCachedStatistics();
  }
}
