package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.investment.event.RunPortfolioCostBasisRebuildFromBaselineRequested;
import ee.tuleva.onboarding.investment.event.RunPortfolioCostBasisSelfHealRequested;
import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import java.util.function.Function;
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
    rebuildEveryFundWithABaseline("self-heal", baseline -> today.minusDays(SELF_HEAL_DAYS), today);
  }

  @EventListener(RunPortfolioCostBasisSelfHealRequested.class)
  void onPortfolioCostBasisSelfHealRequested() {
    run();
  }

  @EventListener(RunPortfolioCostBasisRebuildFromBaselineRequested.class)
  public void rebuildFromBaseline() {
    rebuildEveryFundWithABaseline(
        "rebuild from baseline",
        baseline -> baseline.getBaselineDate().plusDays(1),
        LocalDate.now(clock));
  }

  private void rebuildEveryFundWithABaseline(
      String run, Function<PortfolioBaseline, LocalDate> firstDay, LocalDate today) {
    for (TulevaFund fund : TulevaFund.values()) {
      baselineRepository
          .findByFundIsin(fund.getIsin())
          .ifPresent(baseline -> rebuild(run, fund, firstDay.apply(baseline), today));
    }
  }

  private void rebuild(String run, TulevaFund fund, LocalDate from, LocalDate to) {
    log.info(
        "Rebuilding portfolio cost basis: run={}, fundCode={}, from={}, to={}",
        run,
        fund.getCode(),
        from,
        to);
    try {
      service.rebuildRange(fund, from, to);
    } catch (RuntimeException e) {
      log.error(
          "Cost-basis rebuild failed: run={}, fundCode={}, from={}, to={}, error={}",
          run,
          fund.getCode(),
          from,
          to,
          e.getMessage(),
          e);
    }
  }
}
