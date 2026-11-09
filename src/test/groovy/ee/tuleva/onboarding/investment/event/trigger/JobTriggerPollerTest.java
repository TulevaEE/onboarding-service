package ee.tuleva.onboarding.investment.event.trigger;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
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
import java.util.Optional;
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

  @Mock JobTriggerRepository repository;
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
    assertThat(trigger.getStatus()).isEqualTo("COMPLETED");
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
    assertThat(current.getStatus()).isEqualTo("COMPLETED");
    assertThat(alias.getStatus()).isEqualTo("COMPLETED");
  }

  @Test
  void marksFailedForUnknownJob() {
    var trigger = JobTrigger.builder().jobName("NonExistentJob").status("PENDING").build();
    givenPending(trigger);

    poller.poll();

    assertThat(trigger.getStatus()).isEqualTo("FAILED");
    assertThat(trigger.getErrorMessage()).contains("Unknown job");
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

    assertThat(trigger.getStatus()).isEqualTo("FAILED");
    assertThat(trigger.getErrorMessage()).isEqualTo("boom");
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
    assertThat(trigger.getStatus()).isEqualTo("COMPLETED");
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
    assertThat(trigger.getStatus()).isEqualTo("COMPLETED");
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

    assertThat(trigger.getStatus()).isEqualTo("FAILED");
    then(eventPublisher).should(never()).publishEvent(any());
  }

  @Test
  void aDateOnAJobThatCannotResumeIsMarkedFailed() {
    var trigger =
        JobTrigger.builder().jobName("LimitCheckJob:2026-05-08").status("PENDING").build();
    givenPending(trigger);

    poller.poll();

    assertThat(trigger.getStatus()).isEqualTo("FAILED");
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

    assertThat(trigger.getStatus()).isEqualTo("PENDING");
  }

  @Test
  void aBusyRetryKeepsTheTimeItsTriggerFirstStarted() {
    var firstStart = ClockHolder.clock().instant().minus(Duration.ofMinutes(30));
    var trigger =
        JobTrigger.builder()
            .jobName("SebPendingTransactionReconciliationJob")
            .status("PENDING")
            .createdAt(ClockHolder.clock().instant())
            .startedAt(firstStart)
            .build();
    givenPending(trigger);
    doThrow(new JobBusyException("reconciliation lock held"))
        .when(eventPublisher)
        .publishEvent(any(RunSebPendingTransactionReconciliationRequested.class));

    poller.poll();

    assertThat(trigger.getStartedAt()).isEqualTo(firstStart);
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

    assertThat(trigger.getStatus()).isEqualTo("FAILED");
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

    assertThat(first.getStatus()).isEqualTo("COMPLETED");
    assertThat(second.getStatus()).isEqualTo("PENDING");
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
    assertThat(after.getStatus()).isEqualTo("PENDING");
    then(eventPublisher).should(never()).publishEvent(any(RunLimitCheckRequested.class));
    assertThat(jobTriggerLease.deadline()).isEmpty();
  }

  @Test
  void aTriggerAnotherPollHasAlreadyTakenIsNotRunAgain() {
    var taken = JobTrigger.builder().id(7L).jobName("LimitCheckJob").status("PENDING").build();
    given(repository.findByStatusOrderByCreatedAtAsc("PENDING")).willReturn(List.of(taken));
    given(repository.findByIdAndStatus(7L, "PENDING")).willReturn(Optional.empty());

    poller.poll();

    then(eventPublisher).should(never()).publishEvent(any());
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
    assertThat(phaseUpdate.getStatus()).isEqualTo("COMPLETED");
    assertThat(pevaRavaRecalc.getStatus()).isEqualTo("COMPLETED");
    assertThat(r16Recalc.getStatus()).isEqualTo("COMPLETED");
  }

  @Test
  void publishesTheDailyAndMonthlyFeeCheckEventsSeparately() {
    var daily = JobTrigger.builder().jobName("FeeCheckJob").status("PENDING").build();
    var monthly = JobTrigger.builder().jobName("FeeSettlementCheckJob").status("PENDING").build();
    givenPending(daily, monthly);

    poller.poll();

    then(eventPublisher).should().publishEvent(any(RunFeeCheckRequested.class));
    then(eventPublisher).should().publishEvent(any(RunFeeSettlementCheckRequested.class));
    assertThat(daily.getStatus()).isEqualTo("COMPLETED");
    assertThat(monthly.getStatus()).isEqualTo("COMPLETED");
  }

  @Test
  void processesMultipleTriggers() {
    var trigger1 = JobTrigger.builder().jobName("TrackingDifferenceJob").status("PENDING").build();
    var trigger2 = JobTrigger.builder().jobName("LimitCheckJob").status("PENDING").build();
    givenPending(trigger1, trigger2);

    poller.poll();

    assertThat(trigger1.getStatus()).isEqualTo("COMPLETED");
    assertThat(trigger2.getStatus()).isEqualTo("COMPLETED");
  }

  private void givenPending(JobTrigger... listedAtThePollsStart) {
    for (int i = 0; i < listedAtThePollsStart.length; i++) {
      listedAtThePollsStart[i].setId(i + 1L);
    }
    given(repository.findByStatusOrderByCreatedAtAsc("PENDING"))
        .willReturn(List.of(listedAtThePollsStart));
    given(repository.findByIdAndStatus(any(), eq("PENDING")))
        .willAnswer(
            reload ->
                Arrays.stream(listedAtThePollsStart)
                    .filter(trigger -> trigger.getId().equals(reload.getArgument(0)))
                    .findFirst());
  }
}
