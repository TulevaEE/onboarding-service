package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.investment.event.JobBusyException;
import ee.tuleva.onboarding.investment.event.RunPortfolioCostBasisSelfHealRequested;
import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
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
  static final String LOCK_NAME = "PortfolioCostBasisSelfHealJob";
  private static final Duration LOCK_AT_MOST_FOR = Duration.ofHours(1);

  private final Clock clock;
  private final PortfolioCostBasisService service;
  private final PortfolioBaselineRepository baselineRepository;
  private final CostBasisOversellAlerter oversellAlerter;
  private final LockProvider lockProvider;

  @Scheduled(cron = "0 30 2 * * *", zone = TIMEZONE)
  @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "1h", lockAtLeastFor = "1m")
  public void run() {
    rebuildTheSelfHealWindow();
  }

  @EventListener(RunPortfolioCostBasisSelfHealRequested.class)
  void onPortfolioCostBasisSelfHealRequested() {
    SimpleLock lock =
        lockProvider
            .lock(
                new LockConfiguration(clock.instant(), LOCK_NAME, LOCK_AT_MOST_FOR, Duration.ZERO))
            .orElseThrow(
                () ->
                    new JobBusyException(
                        "Portfolio cost-basis self-heal already running: lockName=" + LOCK_NAME));
    try {
      rebuildTheSelfHealWindow();
    } finally {
      lock.unlock();
    }
  }

  private void rebuildTheSelfHealWindow() {
    LocalDate today = LocalDate.now(clock);
    runForRange(today.minusDays(SELF_HEAL_DAYS), today);
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
      LocalDate fundFrom =
          oversellAlerter.earliestOpenOversell(fund).filter(from::isAfter).orElse(from);
      try {
        service.rebuildRange(fund, fundFrom, to);
        oversellAlerter.clearResolved(fund, fundFrom, to);
      } catch (CostBasisOversellException e) {
        log.error(
            "Self-heal stopped at a cost-basis oversell: fundCode={}, from={}, to={}, error={}",
            fund.getCode(),
            fundFrom,
            to,
            e.getMessage(),
            e);
        oversellAlerter.clearResolved(fund, fundFrom, e.getAsOfDate().minusDays(1));
        oversellAlerter.recordFound(fund, e);
      } catch (RuntimeException e) {
        log.error(
            "Self-heal failed: fundCode={}, from={}, to={}, error={}",
            fund.getCode(),
            fundFrom,
            to,
            e.getMessage(),
            e);
      }
    }
    oversellAlerter.postPending();
  }
}
