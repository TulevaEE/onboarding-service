package ee.tuleva.onboarding.administration;

import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PortfolioAnalyticsScheduler {

  private final PortfolioAnalyticsSource portfolioAnalyticsSource;
  private final PortfolioCsvProcessor portfolioCsvProcessor;
  private final Clock clock;

  @Scheduled(cron = "0 0 10,18 * * ?")
  @SchedulerLock(
      name = "PortfolioAnalyticsScheduler_fetchPortfolioAnalytics",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  @PostConstruct
  public void fetchPortfolioAnalytics() {
    LocalDate today = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate();
    LocalDate yesterday = today.minusDays(1);
    processForDate(today);
    processForDate(yesterday);
  }

  private void processForDate(LocalDate date) {
    portfolioAnalyticsSource
        .fetchCsv(date)
        .ifPresent(inputStream -> portfolioCsvProcessor.process(date, inputStream));
  }
}
