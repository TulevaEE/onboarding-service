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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.ObjectProvider;
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

  private final Clock clock;
  private final PortfolioCostBasisService service;
  private final PortfolioBaselineRepository baselineRepository;
  private final CostBasisOversellAlerter oversellAlerter;
  private final ObjectProvider<LockProvider> lockProviders;

  @Scheduled(cron = "0 30 2 * * *", zone = TIMEZONE)
  @SchedulerLock(name = LOCK_NAME, lockAtMostFor = LOCK_AT_MOST_FOR, lockAtLeastFor = "PT1M")
  public void run() {
    rebuildTheSelfHealWindow();
  }

  @EventListener(RunPortfolioCostBasisSelfHealRequested.class)
  void onPortfolioCostBasisSelfHealRequested() {
    LockProvider lockProvider = lockProviders.getIfAvailable();
    if (lockProvider == null) {
      throw new IllegalStateException(
          "Portfolio cost-basis self-heal refused to run unlocked: no ShedLock LockProvider on"
              + " this node (scheduling.enabled=false), lockName="
              + LOCK_NAME);
    }
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
    boolean everyOversellRecorded = true;
    for (TulevaFund fund : funds) {
      Optional<PortfolioBaseline> baseline = baselineRepository.findByFundIsin(fund.getIsin());
      if (baseline.isPresent()) {
        everyOversellRecorded &= rebuild(fund, baseline.get(), from, to);
      }
    }
    if (everyOversellRecorded) {
      oversellAlerter.postPending();
    } else {
      log.warn(
          "Pending cost-basis oversell alerts held back: an oversell could not be recorded this"
              + " run, from={}, to={}",
          from,
          to);
    }
  }

  private boolean rebuild(
      TulevaFund fund, PortfolioBaseline baseline, LocalDate from, LocalDate to) {
    LocalDate fundFrom = from;
    try {
      boolean replaysFromTheBaseline = oversellAlerter.hasOpenOversell(fund);
      if (replaysFromTheBaseline) {
        fundFrom = earlierOf(from, baseline.getBaselineDate().plusDays(1));
      }
      service.rebuildRange(fund, fundFrom, to);
      if (replaysFromTheBaseline) {
        oversellAlerter.clearOpen(fund, to);
      }
    } catch (CostBasisOversellException e) {
      log.error(
          "Self-heal stopped at a cost-basis oversell: fundCode={}, from={}, to={}, error={}",
          fund.getCode(),
          fundFrom,
          to,
          e.getMessage(),
          e);
      return oversellAlerter.recordFound(fund, e);
    } catch (RuntimeException e) {
      log.error(
          "Self-heal failed: fundCode={}, from={}, to={}, error={}",
          fund.getCode(),
          fundFrom,
          to,
          e.getMessage(),
          e);
    }
    return true;
  }

  private static LocalDate earlierOf(LocalDate first, LocalDate second) {
    return first.isBefore(second) ? first : second;
  }
}
