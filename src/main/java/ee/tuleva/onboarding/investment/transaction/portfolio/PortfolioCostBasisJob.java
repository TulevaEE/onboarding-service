package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.event.RunPortfolioCostBasisRequested;
import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisService;
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
public class PortfolioCostBasisJob {

  private final Clock clock;
  private final PortfolioCostBasisService service;

  @Scheduled(cron = "0 0 10 * * *", zone = TIMEZONE)
  @SchedulerLock(name = "PortfolioCostBasisJob", lockAtMostFor = "30m", lockAtLeastFor = "1m")
  public void run() {
    LocalDate today = LocalDate.now(clock);
    runForDate(today);
  }

  @EventListener
  void onPortfolioCostBasisRequested(RunPortfolioCostBasisRequested event) {
    run();
  }

  public void runForDate(LocalDate date) {
    List<TulevaFund> funds = Arrays.asList(TulevaFund.values());
    log.info("Running portfolio cost-basis job: date={}, fundCount={}", date, funds.size());
    for (TulevaFund fund : funds) {
      try {
        service.runForFundAndDate(fund, date);
      } catch (RuntimeException e) {
        log.error(
            "Failed to update cost basis: fundCode={}, date={}, error={}",
            fund.getCode(),
            date,
            e.getMessage(),
            e);
      }
    }
  }
}
