package ee.tuleva.onboarding.investment.event.trigger;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.investment.event.JobBusyException;
import ee.tuleva.onboarding.investment.event.RunFeeCheckRequested;
import ee.tuleva.onboarding.investment.event.RunFeeSettlementCheckRequested;
import ee.tuleva.onboarding.investment.event.RunLimitCheckRequested;
import ee.tuleva.onboarding.investment.event.RunOverdueSettlementRequested;
import ee.tuleva.onboarding.investment.event.RunPevaRavaFlowRecalcRequested;
import ee.tuleva.onboarding.investment.event.RunPevaRavaPhaseUpdateRequested;
import ee.tuleva.onboarding.investment.event.RunR16FlowRecalcRequested;
import ee.tuleva.onboarding.investment.event.RunSebPendingTransactionReconciliationBackfillRequested;
import ee.tuleva.onboarding.investment.event.RunSebPendingTransactionReconciliationRequested;
import ee.tuleva.onboarding.investment.event.RunSebPendingTransactionReportReconciliationRequested;
import ee.tuleva.onboarding.investment.event.RunSettlementFromBankRequested;
import ee.tuleva.onboarding.investment.event.RunTrackingDifferenceBackfillRequested;
import ee.tuleva.onboarding.investment.event.RunTrackingDifferenceCheckRequested;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class JobTriggerPollerTest {

  @Mock(strictness = Mock.Strictness.LENIENT)
  JobTriggerRepository repository;

  @Mock ApplicationEventPublisher eventPublisher;
  @Mock OperationsNotificationService notificationService;
  @Spy JobTriggerLease jobTriggerLease = new JobTriggerLease();

  @InjectMocks JobTriggerPoller poller;

  @Test
  void publishesEventForPendingTrigger() {
    var trigger = JobTrigger.builder().jobName("TrackingDifferenceJob").status("PENDING").build();
    givenPending(trigger);

    poller.poll();

    then(eventPublisher).should().publishEvent(any(RunTrackingDifferenceCheckRequested.class));
    thenCompleted(trigger);
  }

  @Test
  void publishesCorrectEventType() {
    var trigger = JobTrigger.builder().jobName("LimitCheckJob").status("PENDING").build();
    givenPending(trigger);

    poller.poll();

    var captor = ArgumentCaptor.forClass(Object.class);
    then(eventPublisher).should().publishEvent(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(RunLimitCheckRequested.class);
  }

  @Test
  void settlementCheckJobAndItsOverdueSettlementAliasPublishTheSameEvent() {
    var current = JobTrigger.builder().jobName("SettlementCheckJob").status("PENDING").build();
    var alias = JobTrigger.builder().jobName("OverdueSettlementJob").status("PENDING").build();
    givenPending(current, alias);

    poller.poll();

    then(eventPublisher)
        .should(org.mockito.Mockito.times(2))
        .publishEvent(any(RunOverdueSettlementRequested.class));
    thenCompleted(current);
    thenCompleted(alias);
  }

  @Test
  void aDeferredSettlementFromTheBankTriggerSettlesFromTheBank() {
    var trigger = JobTrigger.builder().jobName("SettlementFromBankJob").status("PENDING").build();
    givenPending(trigger);

    poller.poll();

    then(eventPublisher).should().publishEvent(new RunSettlementFromBankRequested());
    thenCompleted(trigger);
  }

  @Test
  void marksFailedForUnknownJob() {
    var trigger = JobTrigger.builder().jobName("NonExistentJob").status("PENDING").build();
    givenPending(trigger);

    poller.poll();

    thenFailed(trigger, "PENDING", "Unknown job: NonExistentJob");
    then(eventPublisher).should(never()).publishEvent(any());
  }

  @Test
  void marksFailedWhenEventListenerThrows() {
    var trigger = JobTrigger.builder().jobName("TrackingDifferenceJob").status("PENDING").build();
    givenPending(trigger);
    doThrow(new RuntimeException("boom"))
        .when(eventPublisher)
        .publishEvent(any(RunTrackingDifferenceCheckRequested.class));

    poller.poll();

    thenFailed(trigger, "PROCESSING", "boom");
  }

  @Test
  void aPlainBackfillTriggerReplaysTheWholeWindow() {
    var trigger =
        JobTrigger.builder()
            .jobName("SebPendingTransactionReconciliationBackfillJob")
            .status("PENDING")
            .build();
    givenPending(trigger);

    poller.poll();

    then(eventPublisher)
        .should()
        .publishEvent(new RunSebPendingTransactionReconciliationBackfillRequested());
  }

  @Test
  void aBackfillTriggerNamingADateResumesTheReplayFromThatDate() {
    var trigger =
        JobTrigger.builder()
            .jobName("SebPendingTransactionReconciliationBackfillJob:2026-05-08")
            .status("PENDING")
            .build();
    givenPending(trigger);

    poller.poll();

    then(eventPublisher)
        .should()
        .publishEvent(
            new RunSebPendingTransactionReconciliationBackfillRequested(LocalDate.of(2026, 5, 8)));
    thenCompleted(trigger);
  }

  @Test
  void aReportReconciliationTriggerNamingADateReconcilesThatOneReport() {
    var trigger =
        JobTrigger.builder()
            .jobName("SebPendingTransactionReportReconciliationJob:2026-05-16")
            .status("PENDING")
            .build();
    givenPending(trigger);

    poller.poll();

    then(eventPublisher)
        .should()
        .publishEvent(
            new RunSebPendingTransactionReportReconciliationRequested(LocalDate.of(2026, 5, 16)));
    thenCompleted(trigger);
  }

  @Test
  void aBackfillTriggerWithAnUnreadableDateIsMarkedFailed() {
    var trigger =
        JobTrigger.builder()
            .jobName("SebPendingTransactionReconciliationBackfillJob:yesterday")
            .status("PENDING")
            .build();
    givenPending(trigger);

    poller.poll();

    thenFailed(
        trigger,
        "PENDING",
        "Unknown job: SebPendingTransactionReconciliationBackfillJob:yesterday");
    then(eventPublisher).should(never()).publishEvent(any());
  }

  @Test
  void aDateOnAJobThatCannotResumeIsMarkedFailed() {
    var trigger =
        JobTrigger.builder().jobName("LimitCheckJob:2026-05-08").status("PENDING").build();
    givenPending(trigger);

    poller.poll();

    thenFailed(trigger, "PENDING", "Unknown job: LimitCheckJob:2026-05-08");
    then(eventPublisher).should(never()).publishEvent(any());
  }

  @Test
  void aTriggerWhoseJobIsBusyStaysPendingForTheNextPoll() {
    var trigger =
        JobTrigger.builder()
            .jobName("SebPendingTransactionReconciliationJob")
            .status("PENDING")
            .createdAt(ClockHolder.clock().instant())
            .build();
    givenPending(trigger);
    doThrow(new JobBusyException("reconciliation lock held"))
        .when(eventPublisher)
        .publishEvent(any(RunSebPendingTransactionReconciliationRequested.class));

    poller.poll();

    thenRequeued(trigger);
    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void aTriggerWhoseJobStayedBusyForSixHoursIsFailedAndPostedToTheInvestmentChannel() {
    var trigger =
        JobTrigger.builder()
            .jobName("SebPendingTransactionReportReconciliationJob:2026-05-13")
            .status("PENDING")
            .createdAt(ClockHolder.clock().instant().minus(Duration.ofHours(6)))
            .build();
    givenPending(trigger);
    doThrow(new JobBusyException("reconciliation lock held"))
        .when(eventPublisher)
        .publishEvent(any(RunSebPendingTransactionReportReconciliationRequested.class));

    poller.poll();

    thenFailed(trigger, "PROCESSING", "Still busy after 6 hours: reconciliation lock held");
    then(notificationService)
        .should()
        .sendMessage(
            """
            Käivitaja SebPendingTransactionReportReconciliationJob:2026-05-13 aegus 6 tunni \
            järel, selle töö lukk oli kogu aeg hõivatud.
            Käivita uuesti: INSERT INTO investment_job_trigger (job_name) VALUES \
            ('SebPendingTransactionReportReconciliationJob:2026-05-13');""",
            INVESTMENT,
            ERROR);
  }

  @Test
  void anExpiredTriggerWhoseSlackPostFailsStaysFailedAndTheTriggersAfterItStillRun() {
    var expired =
        JobTrigger.builder()
            .jobName("SebPendingTransactionReportReconciliationJob:2026-05-13")
            .status("PENDING")
            .createdAt(ClockHolder.clock().instant().minus(Duration.ofHours(6)))
            .build();
    var after = JobTrigger.builder().jobName("LimitCheckJob").status("PENDING").build();
    givenPending(expired, after);
    doThrow(new JobBusyException("reconciliation lock held"))
        .when(eventPublisher)
        .publishEvent(any(RunSebPendingTransactionReportReconciliationRequested.class));
    willThrow(new IllegalStateException("slack unavailable"))
        .given(notificationService)
        .sendMessage(anyString(), eq(INVESTMENT), eq(ERROR));

    poller.poll();

    thenFailed(expired, "PROCESSING", "Still busy after 6 hours: reconciliation lock held");
    then(eventPublisher).should().publishEvent(any(RunLimitCheckRequested.class));
    thenCompleted(after);
  }

  @AfterEach
  void restoreTheClock() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void aPollStartsNoTriggerOnceItsLeaseIsNearlySpentAndLeavesTheRestPending() {
    MutableClock polling = new MutableClock(Instant.parse("2026-05-18T19:00:00Z"));
    ClockHolder.setClock(polling);
    var first = JobTrigger.builder().jobName("TrackingDifferenceJob").status("PENDING").build();
    var second = JobTrigger.builder().jobName("LimitCheckJob").status("PENDING").build();
    givenPending(first, second);
    willAnswer(
            invocation -> {
              polling.tick(56, MINUTES);
              return null;
            })
        .given(eventPublisher)
        .publishEvent(any(RunTrackingDifferenceCheckRequested.class));

    poller.poll();

    thenCompleted(first);
    thenNotStarted(second);
    then(eventPublisher).should(never()).publishEvent(any(RunLimitCheckRequested.class));
  }

  @Test
  void twoLongReplaysInOnePollEndByItsLeaseDeadlineAndTheTriggerAfterThemWaitsForTheNextPoll() {
    Instant pollStart = Instant.parse("2026-05-18T19:00:00Z");
    MutableClock polling = new MutableClock(pollStart);
    ClockHolder.setClock(polling);
    var replay =
        JobTrigger.builder()
            .jobName("SebPendingTransactionReconciliationBackfillJob")
            .status("PENDING")
            .build();
    var resume =
        JobTrigger.builder()
            .jobName("SebPendingTransactionReconciliationBackfillJob:2026-05-08")
            .status("PENDING")
            .build();
    var after = JobTrigger.builder().jobName("LimitCheckJob").status("PENDING").build();
    givenPending(replay, resume, after);
    List<Instant> deadlinesTheReplaysSaw = new ArrayList<>();
    willAnswer(
            invocation -> {
              Instant deadline = jobTriggerLease.deadline().orElseThrow();
              deadlinesTheReplaysSaw.add(deadline);
              Instant replayStopsAt = polling.instant().plus(Duration.ofMinutes(44));
              polling.tick(
                  Duration.between(
                          polling.instant(),
                          replayStopsAt.isBefore(deadline) ? replayStopsAt : deadline)
                      .toMinutes(),
                  MINUTES);
              return null;
            })
        .given(eventPublisher)
        .publishEvent(any(RunSebPendingTransactionReconciliationBackfillRequested.class));

    poller.poll();

    assertThat(deadlinesTheReplaysSaw)
        .containsExactly(
            pollStart.plus(Duration.ofMinutes(55)), pollStart.plus(Duration.ofMinutes(55)));
    assertThat(polling.instant()).isEqualTo(pollStart.plus(Duration.ofMinutes(55)));
    thenNotStarted(after);
    then(eventPublisher).should(never()).publishEvent(any(RunLimitCheckRequested.class));
    assertThat(jobTriggerLease.deadline()).isEmpty();
  }

  @Test
  void aTriggerAnotherPollHasAlreadyTakenIsNotRunAgain() {
    var taken = JobTrigger.builder().id(7L).jobName("LimitCheckJob").status("PENDING").build();
    given(repository.findByStatusOrderByCreatedAtAsc("PENDING")).willReturn(List.of(taken));
    given(repository.claim(eq(7L), any())).willReturn(0);

    poller.poll();

    then(eventPublisher).should(never()).publishEvent(any());
    then(repository).should(never()).complete(any(), any());
  }

  @Test
  void skipsWhenNoPendingTriggers() {
    given(repository.findByStatusOrderByCreatedAtAsc("PENDING")).willReturn(List.of());

    poller.poll();

    then(eventPublisher).shouldHaveNoInteractions();
  }

  @Test
  void publishesBackfillEventForTrackingDifferenceBackfillJob() {
    var trigger =
        JobTrigger.builder().jobName("TrackingDifferenceBackfillJob").status("PENDING").build();
    givenPending(trigger);

    poller.poll();

    var captor = ArgumentCaptor.forClass(Object.class);
    then(eventPublisher).should().publishEvent(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(RunTrackingDifferenceBackfillRequested.class);
  }

  @Test
  void publishesEpisReportJobEvents() {
    var phaseUpdate =
        JobTrigger.builder().jobName("PevaRavaPhaseUpdateJob").status("PENDING").build();
    var pevaRavaRecalc =
        JobTrigger.builder().jobName("PevaRavaFlowRecalcJob").status("PENDING").build();
    var r16Recalc = JobTrigger.builder().jobName("R16FlowRecalcJob").status("PENDING").build();
    givenPending(phaseUpdate, pevaRavaRecalc, r16Recalc);

    poller.poll();

    then(eventPublisher).should().publishEvent(any(RunPevaRavaPhaseUpdateRequested.class));
    then(eventPublisher).should().publishEvent(any(RunPevaRavaFlowRecalcRequested.class));
    then(eventPublisher).should().publishEvent(any(RunR16FlowRecalcRequested.class));
    thenCompleted(phaseUpdate);
    thenCompleted(pevaRavaRecalc);
    thenCompleted(r16Recalc);
  }

  @Test
  void publishesTheDailyAndMonthlyFeeCheckEventsSeparately() {
    var daily = JobTrigger.builder().jobName("FeeCheckJob").status("PENDING").build();
    var monthly = JobTrigger.builder().jobName("FeeSettlementCheckJob").status("PENDING").build();
    givenPending(daily, monthly);

    poller.poll();

    then(eventPublisher).should().publishEvent(any(RunFeeCheckRequested.class));
    then(eventPublisher).should().publishEvent(any(RunFeeSettlementCheckRequested.class));
    thenCompleted(daily);
    thenCompleted(monthly);
  }

  @Test
  void processesMultipleTriggers() {
    var trigger1 = JobTrigger.builder().jobName("TrackingDifferenceJob").status("PENDING").build();
    var trigger2 = JobTrigger.builder().jobName("LimitCheckJob").status("PENDING").build();
    givenPending(trigger1, trigger2);

    poller.poll();

    thenCompleted(trigger1);
    thenCompleted(trigger2);
  }

  @Test
  void aCompletionThatFindsItsTriggerNoLongerRunningLeavesItAndTheNextTriggerStillRuns() {
    var stoppedByHand =
        JobTrigger.builder().jobName("TrackingDifferenceJob").status("PENDING").build();
    var next = JobTrigger.builder().jobName("LimitCheckJob").status("PENDING").build();
    givenPending(stoppedByHand, next);
    given(repository.complete(eq(stoppedByHand.getId()), any())).willReturn(0);

    poller.poll();

    then(eventPublisher).should().publishEvent(any(RunLimitCheckRequested.class));
    thenCompleted(next);
  }

  @Test
  void aBusyTriggerNoLongerRunningIsLeftAsItIsAndTheNextTriggerStillRuns() {
    var stoppedByHand =
        JobTrigger.builder()
            .jobName("SebPendingTransactionReconciliationJob")
            .status("PENDING")
            .createdAt(ClockHolder.clock().instant())
            .build();
    var next = JobTrigger.builder().jobName("LimitCheckJob").status("PENDING").build();
    givenPending(stoppedByHand, next);
    given(repository.requeue(stoppedByHand.getId())).willReturn(0);
    doThrow(new JobBusyException("reconciliation lock held"))
        .when(eventPublisher)
        .publishEvent(any(RunSebPendingTransactionReconciliationRequested.class));

    poller.poll();

    thenRequeued(stoppedByHand);
    thenCompleted(next);
  }

  @Test
  void anExpiryThatFindsItsTriggerNoLongerRunningPostsNothingAndTheNextTriggerStillRuns() {
    var stoppedByHand =
        JobTrigger.builder()
            .jobName("SebPendingTransactionReportReconciliationJob:2026-05-13")
            .status("PENDING")
            .createdAt(ClockHolder.clock().instant().minus(Duration.ofHours(6)))
            .build();
    var next = JobTrigger.builder().jobName("LimitCheckJob").status("PENDING").build();
    givenPending(stoppedByHand, next);
    given(repository.fail(eq(stoppedByHand.getId()), eq("PROCESSING"), any(), any())).willReturn(0);
    doThrow(new JobBusyException("reconciliation lock held"))
        .when(eventPublisher)
        .publishEvent(any(RunSebPendingTransactionReportReconciliationRequested.class));

    poller.poll();

    then(notificationService).shouldHaveNoInteractions();
    thenCompleted(next);
  }

  private void givenPending(JobTrigger... listedAtThePollsStart) {
    for (int i = 0; i < listedAtThePollsStart.length; i++) {
      listedAtThePollsStart[i].setId(i + 1L);
    }
    given(repository.findByStatusOrderByCreatedAtAsc("PENDING"))
        .willReturn(List.of(listedAtThePollsStart));
    given(repository.claim(any(), any())).willReturn(1);
    given(repository.complete(any(), any())).willReturn(1);
    given(repository.fail(any(), any(), any(), any())).willReturn(1);
    given(repository.requeue(any())).willReturn(1);
    given(repository.findById(any()))
        .willAnswer(
            reread ->
                Arrays.stream(listedAtThePollsStart)
                    .filter(trigger -> trigger.getId().equals(reread.getArgument(0)))
                    .findFirst());
  }

  private void thenCompleted(JobTrigger trigger) {
    then(repository).should().complete(eq(trigger.getId()), any());
  }

  private void thenFailed(JobTrigger trigger, String fromStatus, String errorMessage) {
    then(repository).should().fail(eq(trigger.getId()), eq(fromStatus), any(), eq(errorMessage));
  }

  private void thenRequeued(JobTrigger trigger) {
    then(repository).should().requeue(trigger.getId());
  }

  private void thenNotStarted(JobTrigger trigger) {
    then(repository).should(never()).claim(eq(trigger.getId()), any());
  }
}
