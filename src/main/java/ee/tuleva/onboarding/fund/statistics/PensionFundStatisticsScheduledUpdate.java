package ee.tuleva.onboarding.fund.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
@Profile({"!dev & !staging"})
public class PensionFundStatisticsScheduledUpdate {

  private final PensionFundStatisticsService pensionFundStatisticsService;

  @Scheduled(cron = "0 0 * * * MON-FRI")
  public void refresh() {
    pensionFundStatisticsService.refreshCachedStatistics();
  }
}
