package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.event.RunPortfolioCostBasisSelfHealRequested;
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
public class PortfolioCostBasisSelfHealJob {

  static final int SELF_HEAL_DAYS = 14;

  private final Clock clock;
  private final PortfolioCostBasisService service;
  private final PortfolioBaselineRepository baselineRepository;

  @Scheduled(cron = "0 30 2 * * *", zone = TIMEZONE)
  @SchedulerLock(
      name = "PortfolioCostBasisSelfHealJob",
      lockAtMostFor = "1h",
      lockAtLeastFor = "1m")
  public void run() {
    LocalDate today = LocalDate.now(clock);
    LocalDate from = today.minusDays(SELF_HEAL_DAYS);
    runForRange(from, today);
  }

  @EventListener
  void onPortfolioCostBasisSelfHealRequested(RunPortfolioCostBasisSelfHealRequested event) {
    run();
  }

  public void runForRange(LocalDate from, LocalDate to) {
    List<TulevaFund> funds = Arrays.asList(TulevaFund.values());
    log.info(
        "Running portfolio cost-basis self-heal: from={}, to={}, fundCount={}",
        from,
        to,
        funds.size());
    for (TulevaFund fund : funds) {
      if (baselineRepository.findByFundIsin(fund.getIsin()).isEmpty()) {
        continue;
      }
      try {
        service.rebuildRange(fund, from, to);
      } catch (RuntimeException e) {
        log.error(
            "Self-heal failed: fundCode={}, from={}, to={}, error={}",
            fund.getCode(),
            from,
            to,
            e.getMessage(),
            e);
      }
    }
  }
}
