package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.investment.transaction.portfolio.PortfolioCostBasisSelfHealJob.LOCKS_OF_THE_JOBS_WRITING_COST_BASIS_ROWS;
import static ee.tuleva.onboarding.investment.transaction.portfolio.PortfolioCostBasisSelfHealJob.SELF_HEAL_DAYS;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import ee.tuleva.onboarding.investment.event.JobBusyException;
import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioCostBasisSelfHealJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TODAY = LocalDate.of(2026, 5, 18);
  private static final LocalDate BASELINE_DATE = LocalDate.of(2026, 2, 27);

  @Mock private PortfolioCostBasisService service;
  @Mock private PortfolioBaselineRepository baselineRepository;
  @Mock private LockProvider lockProvider;
  @Mock private SimpleLock lock;

  @Test
  void run_rebuildsLastFourteenDaysForFundsWithBaseline() {
    givenBaselineFor(TUK75);

    jobAt(TODAY.atStartOfDay(TALLINN).toInstant()).run();

    verify(service).rebuildRange(TUK75, TODAY.minusDays(SELF_HEAL_DAYS), TODAY);
    verifyNoMoreInteractions(service);
  }

  @Test
  void onPortfolioCostBasisSelfHealRequested_triggersRun() {
    givenBaselineFor(TUK75);

    jobAt(TODAY.atStartOfDay(TALLINN).toInstant()).onPortfolioCostBasisSelfHealRequested();

    verify(service).rebuildRange(TUK75, TODAY.minusDays(SELF_HEAL_DAYS), TODAY);
  }

  @Test
  void rebuildFromBaseline_rebuildsEachFundFromTheDayAfterItsOwnBaselineToToday() {
    givenBaselineFor(TUK75);
    givenTheLocksAreFree();

    jobAt(TODAY.atStartOfDay(TALLINN).toInstant()).rebuildFromBaseline();

    verify(service).rebuildRange(TUK75, BASELINE_DATE.plusDays(1), TODAY);
    verifyNoMoreInteractions(service);
  }

  @Test
  void rebuildFromBaseline_endsOnTheTallinnDateWhileUtcIsStillOnTheDayBefore() {
    givenBaselineFor(TUK75);
    givenTheLocksAreFree();
    Instant halfPastOneInTallinn = TODAY.atTime(1, 30).atZone(TALLINN).toInstant();

    jobAt(halfPastOneInTallinn).rebuildFromBaseline();

    verify(service).rebuildRange(TUK75, BASELINE_DATE.plusDays(1), TODAY);
  }

  @Test
  void rebuildFromBaseline_failsTheTriggerAfterRebuildingTheOtherFundsWhenOneFails() {
    for (TulevaFund fund : TulevaFund.values()) {
      givenBaselineFor(fund);
    }
    givenTheLocksAreFree();
    willThrow(new IllegalStateException("Cost-basis oversell"))
        .given(service)
        .rebuildRange(TUV100, BASELINE_DATE.plusDays(1), TODAY);

    assertThatThrownBy(() -> jobAt(TODAY.atStartOfDay(TALLINN).toInstant()).rebuildFromBaseline())
        .isInstanceOf(IllegalStateException.class);

    for (TulevaFund fund : TulevaFund.values()) {
      verify(service).rebuildRange(fund, BASELINE_DATE.plusDays(1), TODAY);
    }
    verify(lock, times(LOCKS_OF_THE_JOBS_WRITING_COST_BASIS_ROWS.size())).unlock();
  }

  @Test
  void rebuildFromBaseline_holdsTheLocksOfBothCostBasisJobsAndReleasesThem() {
    givenBaselineFor(TUK75);
    givenTheLocksAreFree();

    jobAt(TODAY.atStartOfDay(TALLINN).toInstant()).rebuildFromBaseline();

    for (String name : LOCKS_OF_THE_JOBS_WRITING_COST_BASIS_ROWS) {
      verify(lockProvider).lock(argThat(configuration -> configuration.getName().equals(name)));
    }
    verify(lock, times(LOCKS_OF_THE_JOBS_WRITING_COST_BASIS_ROWS.size())).unlock();
  }

  @Test
  void rebuildFromBaseline_waitsForTheNextPollWhileACostBasisJobHoldsItsLock() {
    given(lockProvider.lock(any()))
        .willAnswer(
            invocation ->
                invocation
                        .<LockConfiguration>getArgument(0)
                        .getName()
                        .equals("PortfolioCostBasisJob")
                    ? Optional.empty()
                    : Optional.of(lock));

    assertThatThrownBy(() -> jobAt(TODAY.atStartOfDay(TALLINN).toInstant()).rebuildFromBaseline())
        .isInstanceOf(JobBusyException.class);

    verifyNoInteractions(service);
    verify(lock).unlock();
  }

  @Test
  void run_takesNoLockOfItsOwnBesideItsSchedulerLock() {
    givenBaselineFor(TUK75);

    jobAt(TODAY.atStartOfDay(TALLINN).toInstant()).run();

    verify(lockProvider, never()).lock(any());
  }

  private void givenBaselineFor(TulevaFund fund) {
    given(baselineRepository.findByFundIsin(fund.getIsin()))
        .willReturn(
            Optional.of(
                PortfolioBaseline.builder()
                    .fundIsin(fund.getIsin())
                    .baselineDate(BASELINE_DATE)
                    .build()));
  }

  private void givenTheLocksAreFree() {
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));
  }

  private PortfolioCostBasisSelfHealJob jobAt(Instant now) {
    return new PortfolioCostBasisSelfHealJob(
        Clock.fixed(now, ZoneOffset.UTC), service, baselineRepository, lockProvider);
  }
}
