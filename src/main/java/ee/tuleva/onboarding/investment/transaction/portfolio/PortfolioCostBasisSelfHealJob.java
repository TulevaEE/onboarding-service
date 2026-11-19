package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.investment.event.JobBusyException;
import ee.tuleva.onboarding.investment.event.RunPortfolioCostBasisRebuildFromBaselineRequested;
import ee.tuleva.onboarding.investment.event.RunPortfolioCostBasisSelfHealRequested;
import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
  static final List<String> LOCKS_OF_THE_JOBS_WRITING_COST_BASIS_ROWS =
      List.of("PortfolioCostBasisSelfHealJob", "PortfolioCostBasisJob");
  private static final Duration A_STOPPED_REBUILD_HOLDS_THE_LOCKS_AT_MOST = Duration.ofHours(3);

  private final Clock clock;
  private final PortfolioCostBasisService service;
  private final PortfolioBaselineRepository baselineRepository;
  private final LockProvider lockProvider;

  @Scheduled(cron = "0 30 2 * * *", zone = TIMEZONE)
  @SchedulerLock(
      name = "PortfolioCostBasisSelfHealJob",
      lockAtMostFor = "1h",
      lockAtLeastFor = "1m")
  public void run() {
    LocalDate today = tallinnToday();
    for (TulevaFund fund : TulevaFund.values()) {
      if (baselineRepository.findByFundIsin(fund.getIsin()).isPresent()) {
        rebuilt(fund, today.minusDays(SELF_HEAL_DAYS), today);
      }
    }
  }

  @EventListener(RunPortfolioCostBasisSelfHealRequested.class)
  void onPortfolioCostBasisSelfHealRequested() {
    run();
  }

  @EventListener(RunPortfolioCostBasisRebuildFromBaselineRequested.class)
  public void rebuildFromBaseline() {
    List<SimpleLock> locks = lockEveryJobWritingCostBasisRows();
    try {
      LocalDate today = tallinnToday();
      List<String> failedFundCodes = new ArrayList<>();
      for (TulevaFund fund : TulevaFund.values()) {
        Optional<PortfolioBaseline> baseline = baselineRepository.findByFundIsin(fund.getIsin());
        if (baseline.isPresent()
            && !rebuilt(fund, baseline.get().getBaselineDate().plusDays(1), today)) {
          failedFundCodes.add(fund.getCode());
        }
      }
      if (!failedFundCodes.isEmpty()) {
        throw new IllegalStateException(
            "Cost-basis rebuild from baseline failed: fundCodes=" + failedFundCodes);
      }
    } finally {
      locks.forEach(SimpleLock::unlock);
    }
  }

  private List<SimpleLock> lockEveryJobWritingCostBasisRows() {
    List<SimpleLock> held = new ArrayList<>();
    for (String name : LOCKS_OF_THE_JOBS_WRITING_COST_BASIS_ROWS) {
      Optional<SimpleLock> lock =
          lockProvider.lock(
              new LockConfiguration(
                  clock.instant(), name, A_STOPPED_REBUILD_HOLDS_THE_LOCKS_AT_MOST, Duration.ZERO));
      if (lock.isEmpty()) {
        held.forEach(SimpleLock::unlock);
        throw new JobBusyException(
            "Cost-basis rebuild from baseline waits for a running cost-basis job: lock=" + name);
      }
      held.add(lock.get());
    }
    return held;
  }

  private LocalDate tallinnToday() {
    return LocalDate.now(clock.withZone(ZoneId.of(TIMEZONE)));
  }

  private boolean rebuilt(TulevaFund fund, LocalDate from, LocalDate to) {
    log.info(
        "Rebuilding portfolio cost basis: fundCode={}, from={}, to={}", fund.getCode(), from, to);
    try {
      service.rebuildRange(fund, from, to);
      return true;
    } catch (RuntimeException e) {
      log.error(
          "Cost-basis rebuild failed: fundCode={}, from={}, to={}, error={}",
          fund.getCode(),
          from,
          to,
          e.getMessage(),
          e);
      return false;
    }
  }
}
