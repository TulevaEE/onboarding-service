package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.event.RunPortfolioReconciliationRequested;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"production", "staging"})
@RequiredArgsConstructor
public class PortfolioReconciliationJob {

  private final Clock clock;
  private final PortfolioReconciliationService service;

  @Scheduled(cron = "0 30 10 * * *", zone = TIMEZONE)
  @SchedulerLock(
      name = "portfolio-reconciliation-job",
      lockAtMostFor = "10m",
      lockAtLeastFor = "1m")
  public void run() {
    LocalDate today = LocalDate.now(clock);
    runForDate(today);
  }

  @EventListener
  void onPortfolioReconciliationRequested(RunPortfolioReconciliationRequested event) {
    run();
  }

  public void runForDate(LocalDate date) {
    List<TulevaFund> funds = Arrays.asList(TulevaFund.values());
    log.info("Running portfolio reconciliation job: date={}, fundCount={}", date, funds.size());
    for (TulevaFund fund : funds) {
      try {
        service.reconcile(fund, date);
      } catch (RuntimeException e) {
        log.error(
            "Portfolio reconciliation failed: fundCode={}, date={}, error={}",
            fund.getCode(),
            date,
            e.getMessage(),
            e);
      }
    }
  }
}
