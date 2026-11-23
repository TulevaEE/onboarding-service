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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
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
  static final String LOCK_AT_MOST_FOR = "PT6H";
  static final List<String> LOCKS_OF_THE_JOBS_WRITING_COST_BASIS_ROWS =
      List.of(LOCK_NAME, "PortfolioCostBasisJob");
  private static final Duration A_STOPPED_REBUILD_HOLDS_THE_LOCKS_AT_MOST =
      Duration.parse(LOCK_AT_MOST_FOR);

  private enum Rebuild {
    REBUILT,
    OVERSELL_RECORDED,
    OVERSELL_UNRECORDED,
    FAILED
  }

  private final Clock clock;
  private final PortfolioCostBasisService service;
  private final PortfolioBaselineRepository baselineRepository;
  private final CostBasisOversellAlerter oversellAlerter;
  private final LockProvider lockProvider;

  @Scheduled(cron = "0 30 2 * * *", zone = TIMEZONE)
  @SchedulerLock(name = LOCK_NAME, lockAtMostFor = LOCK_AT_MOST_FOR, lockAtLeastFor = "PT1M")
  public void run() {
    rebuildTheSelfHealWindow();
  }

  @EventListener(RunPortfolioCostBasisSelfHealRequested.class)
  void onPortfolioCostBasisSelfHealRequested() {
    SimpleLock lock =
        lockProvider
            .lock(
                new LockConfiguration(
                    clock.instant(), LOCK_NAME, Duration.parse(LOCK_AT_MOST_FOR), Duration.ZERO))
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

  @EventListener(RunPortfolioCostBasisRebuildFromBaselineRequested.class)
  public void rebuildFromBaseline() {
    List<SimpleLock> locks = lockEveryJobWritingCostBasisRows();
    try {
      List<String> failedFundCodes =
          rebuildEveryFundWithABaseline(
                  baseline -> baseline.getBaselineDate().plusDays(1), tallinnToday())
              .entrySet()
              .stream()
              .filter(rebuild -> rebuild.getValue() != Rebuild.REBUILT)
              .map(rebuild -> rebuild.getKey().getCode())
              .toList();
      if (!failedFundCodes.isEmpty()) {
        throw new IllegalStateException(
            "Cost-basis rebuild from baseline failed: fundCodes=" + failedFundCodes);
      }
    } finally {
      locks.forEach(SimpleLock::unlock);
    }
  }

  void runForRange(LocalDate from, LocalDate to) {
    log.info("Running portfolio cost-basis self-heal: from={}, to={}", from, to);
    rebuildEveryFundWithABaseline(baseline -> from, to);
  }

  private void rebuildTheSelfHealWindow() {
    LocalDate today = tallinnToday();
    runForRange(today.minusDays(SELF_HEAL_DAYS), today);
  }

  private Map<TulevaFund, Rebuild> rebuildEveryFundWithABaseline(
      Function<PortfolioBaseline, LocalDate> fromOf, LocalDate to) {
    Map<TulevaFund, Rebuild> rebuilds = new EnumMap<>(TulevaFund.class);
    for (TulevaFund fund : TulevaFund.values()) {
      Optional<PortfolioBaseline> baseline = baselineRepository.findByFundIsin(fund.getIsin());
      if (baseline.isPresent()) {
        rebuilds.put(fund, rebuild(fund, baseline.get(), fromOf.apply(baseline.get()), to));
      }
    }
    if (rebuilds.containsValue(Rebuild.OVERSELL_UNRECORDED)) {
      log.warn(
          "Pending cost-basis oversell alerts held back: an oversell could not be recorded this"
              + " run, to={}",
          to);
    } else {
      oversellAlerter.postPending();
    }
    return rebuilds;
  }

  private Rebuild rebuild(
      TulevaFund fund, PortfolioBaseline baseline, LocalDate from, LocalDate to) {
    LocalDate fundFrom = from;
    try {
      boolean replaysFromTheBaseline = oversellAlerter.hasOpenOversell(fund);
      if (replaysFromTheBaseline) {
        fundFrom = earlierOf(from, baseline.getBaselineDate().plusDays(1));
      }
      log.info(
          "Rebuilding portfolio cost basis: fundCode={}, from={}, to={}",
          fund.getCode(),
          fundFrom,
          to);
      service.rebuildRange(fund, fundFrom, to);
      if (replaysFromTheBaseline) {
        oversellAlerter.clearOpen(fund, to);
      }
      return Rebuild.REBUILT;
    } catch (CostBasisOversellException e) {
      log.error(
          "Cost-basis rebuild stopped at an oversell: fundCode={}, from={}, to={}, error={}",
          fund.getCode(),
          fundFrom,
          to,
          e.getMessage(),
          e);
      return oversellAlerter.recordFound(fund, e)
          ? Rebuild.OVERSELL_RECORDED
          : Rebuild.OVERSELL_UNRECORDED;
    } catch (RuntimeException e) {
      log.error(
          "Cost-basis rebuild failed: fundCode={}, from={}, to={}, error={}",
          fund.getCode(),
          fundFrom,
          to,
          e.getMessage(),
          e);
      return Rebuild.FAILED;
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

  private static LocalDate earlierOf(LocalDate first, LocalDate second) {
    return first.isBefore(second) ? first : second;
  }
}
