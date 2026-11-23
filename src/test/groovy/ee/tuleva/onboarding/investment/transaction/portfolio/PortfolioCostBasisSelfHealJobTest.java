package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.investment.transaction.portfolio.PortfolioCostBasisSelfHealJob.LOCKS_OF_THE_JOBS_WRITING_COST_BASIS_ROWS;
import static ee.tuleva.onboarding.investment.transaction.portfolio.PortfolioCostBasisSelfHealJob.LOCK_NAME;
import static ee.tuleva.onboarding.investment.transaction.portfolio.PortfolioCostBasisSelfHealJob.SELF_HEAL_DAYS;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import ee.tuleva.onboarding.investment.event.JobBusyException;
import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioCostBasisSelfHealJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TODAY = LocalDate.of(2026, 5, 18);
  private static final LocalDate WINDOW_START = TODAY.minusDays(SELF_HEAL_DAYS);
  private static final LocalDate BASELINE_DATE = LocalDate.of(2026, 2, 27);

  @Spy private Clock clock = Clock.fixed(TODAY.atStartOfDay(TALLINN).toInstant(), TALLINN);

  @Mock private PortfolioCostBasisService service;
  @Mock private PortfolioBaselineRepository baselineRepository;
  @Mock private CostBasisOversellAlerter oversellAlerter;
  @Mock private LockProvider lockProvider;
  @Mock private SimpleLock lock;

  @InjectMocks private PortfolioCostBasisSelfHealJob job;

  @Test
  void run_rebuildsLastFourteenDaysForFundsWithBaseline() {
    givenBaselinesFor(TODAY, TUK75);

    job.run();

    verify(service).rebuildRange(TUK75, WINDOW_START, TODAY);
    for (TulevaFund fund : TulevaFund.values()) {
      if (fund != TUK75) {
        verify(service, never()).rebuildRange(fund, WINDOW_START, TODAY);
      }
    }
  }

  @Test
  void run_endsItsWindowOnTheTallinnDateWhileUtcIsStillOnTheDayBefore() {
    givenBaselinesFor(TODAY, TUK75);
    Instant halfPastOneInTallinn = TODAY.atTime(1, 30).atZone(TALLINN).toInstant();

    jobAt(halfPastOneInTallinn).run();

    verify(service).rebuildRange(TUK75, WINDOW_START, TODAY);
  }

  @Test
  void run_recordsAnOversellCarriesOnWithTheOtherFundsAndPostsOnceAtTheEnd() {
    givenBaselinesFor(TODAY, TUK75, TUK00);
    CostBasisOversellException oversell = oversell(WINDOW_START);
    willThrow(oversell).given(service).rebuildRange(TUK75, WINDOW_START, TODAY);
    given(oversellAlerter.recordFound(TUK75, oversell)).willReturn(true);

    job.run();

    InOrder inOrder = inOrder(service, oversellAlerter);
    inOrder.verify(oversellAlerter).recordFound(TUK75, oversell);
    inOrder.verify(service).rebuildRange(TUK00, WINDOW_START, TODAY);
    inOrder.verify(oversellAlerter).postPending();
    verify(oversellAlerter, never()).clearOpen(any(), any());
  }

  @Test
  void run_holdsBackPendingPostsWhenAnOversellCouldNotBeRecorded() {
    givenBaselinesFor(TODAY, TUK75);
    CostBasisOversellException oversell = oversell(WINDOW_START);
    willThrow(oversell).given(service).rebuildRange(TUK75, WINDOW_START, TODAY);
    given(oversellAlerter.recordFound(TUK75, oversell)).willReturn(false);

    job.run();

    verify(oversellAlerter, never()).postPending();
  }

  @Test
  void run_replaysAFundFromItsBaselineWhileItHasAnOpenOversellAndClearsItWhenTheReplaySucceeds() {
    LocalDate baselineDate = TODAY.minusDays(100);
    givenBaselinesFor(baselineDate, TUK75);
    given(oversellAlerter.hasOpenOversell(TUK75)).willReturn(true);

    job.run();

    verify(service).rebuildRange(TUK75, baselineDate.plusDays(1), TODAY);
    verify(oversellAlerter).clearOpen(TUK75, TODAY);
  }

  @Test
  void run_keepsAnOversellOpenWhenTheReplayStillStopsAtAnOversell() {
    LocalDate baselineDate = TODAY.minusDays(100);
    givenBaselinesFor(baselineDate, TUK75);
    given(oversellAlerter.hasOpenOversell(TUK75)).willReturn(true);
    CostBasisOversellException oversell = oversell(TODAY.minusDays(40));
    willThrow(oversell).given(service).rebuildRange(TUK75, baselineDate.plusDays(1), TODAY);

    job.run();

    verify(oversellAlerter).recordFound(TUK75, oversell);
    verify(oversellAlerter, never()).clearOpen(any(), any());
  }

  @Test
  void run_keepsItsWindowWhenTheBaselineIsInsideIt() {
    givenBaselinesFor(TODAY.minusDays(3), TUK75);
    given(oversellAlerter.hasOpenOversell(TUK75)).willReturn(true);

    job.run();

    verify(service).rebuildRange(TUK75, WINDOW_START, TODAY);
  }

  @Test
  void run_neitherRecordsNorClearsAnOversellForAFailureOfAnotherKind() {
    givenBaselinesFor(TODAY, TUK75);
    given(oversellAlerter.hasOpenOversell(TUK75)).willReturn(true);
    willThrow(new IllegalStateException("Execution refers to missing order: orderId=1"))
        .given(service)
        .rebuildRange(TUK75, WINDOW_START, TODAY);

    job.run();

    verify(oversellAlerter, never()).recordFound(any(), any());
    verify(oversellAlerter, never()).clearOpen(any(), any());
    verify(oversellAlerter).postPending();
  }

  @Test
  void run_skipsAFundWhoseOversellHistoryCannotBeReadAndCarriesOnWithTheOthers() {
    givenBaselinesFor(TODAY, TUK75, TUK00);
    given(oversellAlerter.hasOpenOversell(TUK75))
        .willThrow(new IllegalStateException("database unavailable"));

    job.run();

    verify(service, never()).rebuildRange(TUK75, WINDOW_START, TODAY);
    verify(service).rebuildRange(TUK00, WINDOW_START, TODAY);
  }

  @Test
  void run_takesNoLockOfItsOwnBesideItsSchedulerLock() {
    givenBaselinesFor(TODAY, TUK75);

    job.run();

    verify(lockProvider, never()).lock(any());
  }

  @Test
  void aManualSelfHealRunsUnderTheScheduledRunsLockAndReleasesItOnlyAfterPosting() {
    AtomicBoolean released = new AtomicBoolean();
    given(lockProvider.lock(argThat(configuration -> configuration.getName().equals(LOCK_NAME))))
        .willReturn(Optional.of(() -> released.set(true)));
    givenBaselinesFor(TODAY, TUK75);
    willAnswer(
            invocation -> {
              assertThat(released).isFalse();
              return null;
            })
        .given(oversellAlerter)
        .postPending();

    job.onPortfolioCostBasisSelfHealRequested();

    InOrder inOrder = inOrder(lockProvider, service, oversellAlerter);
    inOrder.verify(lockProvider).lock(any());
    inOrder.verify(service).rebuildRange(TUK75, WINDOW_START, TODAY);
    inOrder.verify(oversellAlerter).postPending();
    assertThat(released).isTrue();
  }

  @Test
  void aManualSelfHealWhileTheScheduledRunHoldsTheLockIsLeftForTheNextPoll() {
    given(lockProvider.lock(any())).willReturn(Optional.empty());

    assertThatThrownBy(() -> job.onPortfolioCostBasisSelfHealRequested())
        .isInstanceOf(JobBusyException.class);

    verifyNoInteractions(service, oversellAlerter);
  }

  @Test
  void rebuildFromBaseline_rebuildsEachFundFromTheDayAfterItsOwnBaselineToToday() {
    givenBaselinesFor(BASELINE_DATE, TUK75);
    givenTheLocksAreFree();

    job.rebuildFromBaseline();

    verify(service).rebuildRange(TUK75, BASELINE_DATE.plusDays(1), TODAY);
    verifyNoMoreInteractions(service);
  }

  @Test
  void rebuildFromBaseline_endsOnTheTallinnDateWhileUtcIsStillOnTheDayBefore() {
    givenBaselinesFor(BASELINE_DATE, TUK75);
    givenTheLocksAreFree();
    Instant halfPastOneInTallinn = TODAY.atTime(1, 30).atZone(TALLINN).toInstant();

    jobAt(halfPastOneInTallinn).rebuildFromBaseline();

    verify(service).rebuildRange(TUK75, BASELINE_DATE.plusDays(1), TODAY);
  }

  @Test
  void rebuildFromBaseline_failsTheTriggerAfterRebuildingTheOtherFundsWhenOneFails() {
    givenBaselinesFor(BASELINE_DATE, TulevaFund.values());
    givenTheLocksAreFree();
    willThrow(new IllegalStateException("Execution refers to missing order: orderId=1"))
        .given(service)
        .rebuildRange(TUV100, BASELINE_DATE.plusDays(1), TODAY);

    assertThatThrownBy(() -> job.rebuildFromBaseline()).isInstanceOf(IllegalStateException.class);

    for (TulevaFund fund : TulevaFund.values()) {
      verify(service).rebuildRange(fund, BASELINE_DATE.plusDays(1), TODAY);
    }
    verify(lock, times(LOCKS_OF_THE_JOBS_WRITING_COST_BASIS_ROWS.size())).unlock();
  }

  @Test
  void rebuildFromBaseline_recordsAndPostsAnOversellItStopsAtAndFailsTheTrigger() {
    givenBaselinesFor(BASELINE_DATE, TUK75);
    givenTheLocksAreFree();
    CostBasisOversellException oversell = oversell(TODAY.minusDays(40));
    willThrow(oversell).given(service).rebuildRange(TUK75, BASELINE_DATE.plusDays(1), TODAY);
    given(oversellAlerter.recordFound(TUK75, oversell)).willReturn(true);

    assertThatThrownBy(() -> job.rebuildFromBaseline()).isInstanceOf(IllegalStateException.class);

    InOrder inOrder = inOrder(oversellAlerter, lock);
    inOrder.verify(oversellAlerter).recordFound(TUK75, oversell);
    inOrder.verify(oversellAlerter).postPending();
    inOrder.verify(lock, times(LOCKS_OF_THE_JOBS_WRITING_COST_BASIS_ROWS.size())).unlock();
  }

  @Test
  void rebuildFromBaseline_clearsAnOpenOversellOnceItsReplaySucceeds() {
    givenBaselinesFor(BASELINE_DATE, TUK75);
    givenTheLocksAreFree();
    given(oversellAlerter.hasOpenOversell(TUK75)).willReturn(true);

    job.rebuildFromBaseline();

    verify(service).rebuildRange(TUK75, BASELINE_DATE.plusDays(1), TODAY);
    verify(oversellAlerter).clearOpen(TUK75, TODAY);
    verify(oversellAlerter).postPending();
  }

  @Test
  void rebuildFromBaseline_holdsTheLocksOfBothCostBasisJobsAndReleasesThem() {
    givenBaselinesFor(BASELINE_DATE, TUK75);
    givenTheLocksAreFree();

    job.rebuildFromBaseline();

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

    assertThatThrownBy(() -> job.rebuildFromBaseline()).isInstanceOf(JobBusyException.class);

    verifyNoInteractions(service, oversellAlerter);
    verify(lock).unlock();
  }

  private static CostBasisOversellException oversell(LocalDate asOfDate) {
    return new CostBasisOversellException(
        TUK75.getIsin(), "IE00BFNM3G45", asOfDate, new BigDecimal("100"), new BigDecimal("150"));
  }

  private void givenBaselinesFor(LocalDate baselineDate, TulevaFund... fundsWithBaseline) {
    List<TulevaFund> withBaseline = List.of(fundsWithBaseline);
    for (TulevaFund fund : TulevaFund.values()) {
      given(baselineRepository.findByFundIsin(fund.getIsin()))
          .willReturn(
              withBaseline.contains(fund)
                  ? Optional.of(
                      PortfolioBaseline.builder()
                          .fundIsin(fund.getIsin())
                          .baselineDate(baselineDate)
                          .build())
                  : Optional.empty());
    }
  }

  private void givenTheLocksAreFree() {
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));
  }

  private PortfolioCostBasisSelfHealJob jobAt(Instant now) {
    return new PortfolioCostBasisSelfHealJob(
        Clock.fixed(now, ZoneOffset.UTC),
        service,
        baselineRepository,
        oversellAlerter,
        lockProvider);
  }
}
