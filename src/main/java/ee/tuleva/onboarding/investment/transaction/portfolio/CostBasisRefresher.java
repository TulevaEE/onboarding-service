package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.transaction.portfolio.PortfolioCostBasisSelfHealJob.LOCKS_OF_THE_JOBS_WRITING_COST_BASIS_ROWS;
import static ee.tuleva.onboarding.investment.transaction.portfolio.PortfolioCostBasisSelfHealJob.SELF_HEAL_DAYS;
import static java.util.function.Predicate.not;

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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"production", "staging"})
@RequiredArgsConstructor
public class CostBasisRefresher {

  private static final Duration A_STOPPED_REFRESH_HOLDS_THE_LOCKS_AT_MOST = Duration.ofMinutes(30);
  private static final int AN_ALERT_LINE_HOLDS_AT_MOST = 300;

  private final Clock clock;
  private final PortfolioCostBasisService service;
  private final PortfolioBaselineRepository baselineRepository;
  private final ObjectProvider<LockProvider> lockProviders;

  public CostBasisRefresh refresh(TulevaFund fund) {
    try {
      return refreshTheSelfHealWindow(fund);
    } catch (RuntimeException e) {
      log.error(
          "Cost-basis refresh before reconciliation failed, the stored ledger is compared:"
              + " fundCode={}, error={}",
          fund.getCode(),
          e.getMessage(),
          e);
      return CostBasisRefresh.failed(describe(e));
    }
  }

  private CostBasisRefresh refreshTheSelfHealWindow(TulevaFund fund) {
    if (baselineRepository.findByFundIsin(fund.getIsin()).isEmpty()) {
      return CostBasisRefresh.noBaseline();
    }
    Optional<List<SimpleLock>> locks = lockEveryJobWritingCostBasisRows();
    if (locks.isEmpty()) {
      log.warn(
          "Cost-basis refresh before reconciliation skipped, the cost-basis locks were not taken:"
              + " fundCode={}",
          fund.getCode());
      return CostBasisRefresh.lockNotTaken();
    }
    try {
      LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(TIMEZONE)));
      service.rebuildRange(fund, today.minusDays(SELF_HEAL_DAYS), today);
      return CostBasisRefresh.refreshed();
    } finally {
      releaseEach(locks.get());
    }
  }

  private Optional<List<SimpleLock>> lockEveryJobWritingCostBasisRows() {
    LockProvider lockProvider = lockProviders.getIfAvailable();
    if (lockProvider == null) {
      throw new IllegalStateException(
          "Cost-basis refresh refused to run unlocked: no ShedLock LockProvider on this node");
    }
    List<SimpleLock> held = new ArrayList<>();
    try {
      for (String name : LOCKS_OF_THE_JOBS_WRITING_COST_BASIS_ROWS) {
        Optional<SimpleLock> lock = lockProvider.lock(lockConfiguration(name));
        if (lock.isEmpty()) {
          releaseEach(held);
          return Optional.empty();
        }
        held.add(lock.get());
      }
    } catch (RuntimeException e) {
      releaseEach(held);
      throw e;
    }
    return Optional.of(held);
  }

  private static void releaseEach(List<SimpleLock> locks) {
    locks.forEach(CostBasisRefresher::release);
  }

  private static void release(SimpleLock lock) {
    try {
      lock.unlock();
    } catch (RuntimeException e) {
      log.error(
          "Cost-basis lock release failed, the lock expires on its own: error={}",
          e.getMessage(),
          e);
    }
  }

  private LockConfiguration lockConfiguration(String name) {
    return new LockConfiguration(
        clock.instant(), name, A_STOPPED_REFRESH_HOLDS_THE_LOCKS_AT_MOST, Duration.ZERO);
  }

  private static String describe(RuntimeException e) {
    return Optional.ofNullable(e.getMessage())
        .flatMap(
            message -> message.lines().map(String::strip).filter(not(String::isEmpty)).findFirst())
        .map(CostBasisRefresher::asOneAlertLine)
        .orElseGet(() -> e.getClass().getSimpleName());
  }

  private static String asOneAlertLine(String line) {
    String withoutFullStop = line.endsWith(".") ? line.substring(0, line.length() - 1) : line;
    if (withoutFullStop.length() <= AN_ALERT_LINE_HOLDS_AT_MOST) {
      return withoutFullStop;
    }
    return withoutFullStop.substring(0, AN_ALERT_LINE_HOLDS_AT_MOST) + "…";
  }
}
