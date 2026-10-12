package ee.tuleva.onboarding.mandate.batch.poller;

import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFundPensionOpeningMandate;
import static ee.tuleva.onboarding.mandate.MandateFixture.samplePartialWithdrawalMandate;
import static ee.tuleva.onboarding.mandate.batch.poller.MandateBatchProcessingPoller.MAX_POLL_COUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ee.tuleva.onboarding.auth.SecurityContextRunner;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.mandate.batch.MandateBatchCompletion;
import ee.tuleva.onboarding.mandate.batch.MandateBatchFixture;
import ee.tuleva.onboarding.mandate.batch.poller.MandateBatchProcessingPoller.MandateBatchPollingContext;
import ee.tuleva.onboarding.mandate.exception.MandateProcessingException;
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class MandateBatchProcessingPollerTest {

  @Mock private MandateProcessorService mandateProcessor;
  @Mock private MandateBatchCompletion mandateBatchCompletion;
  @Mock private SecurityContextRunner securityContextRunner;

  @InjectMocks private MandateBatchProcessingPoller mandateBatchProcessingPoller;

  private final Logger pollerLogger =
      (Logger) LoggerFactory.getLogger(MandateBatchProcessingPoller.class);
  private final ListAppender<ILoggingEvent> logEvents = new ListAppender<>();

  @BeforeEach
  void capturePollerLog() {
    logEvents.start();
    pollerLogger.addAppender(logEvents);
  }

  @AfterEach
  void tearDown() {
    pollerLogger.detachAppender(logEvents);
    mandateBatchProcessingPoller.stop();
  }

  private void runActionsAsTheGivenPerson() {
    willAnswer(
            invocation -> {
              invocation.getArgument(1, Runnable.class).run();
              return null;
            })
        .given(securityContextRunner)
        .runAs(any(Person.class), any(Runnable.class));
  }

  @SneakyThrows
  @SuppressWarnings("unchecked")
  private Queue<MandateBatchPollingContext> getMockQueue() {
    var field = MandateBatchProcessingPoller.class.getDeclaredField("batchPollingQueue");

    var mockQueue = mock(ConcurrentLinkedQueue.class);

    field.setAccessible(true);
    field.set(mandateBatchProcessingPoller, mockQueue);

    return mockQueue;
  }

  @SneakyThrows
  @SuppressWarnings("unchecked")
  private ExecutorService getMockPoller() {
    var field = MandateBatchProcessingPoller.class.getDeclaredField("poller");

    var mockPoller = mock(ExecutorService.class);

    field.setAccessible(true);
    field.set(mandateBatchProcessingPoller, mockPoller);

    return mockPoller;
  }

  @Test
  @DisplayName("Should start polling")
  void shouldStart() {
    var locale = Locale.ENGLISH;

    Mandate mandate1 = sampleFundPensionOpeningMandate();
    Mandate mandate2 = samplePartialWithdrawalMandate();

    var mandateBatch = MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));

    assertDoesNotThrow(
        () ->
            mandateBatchProcessingPoller.startPollingForBatchProcessingFinished(
                mandateBatch, locale));
  }

  @Test
  @DisplayName("Should process queue")
  void shouldProcessQueue() {

    var mockPoller = getMockPoller();

    mandateBatchProcessingPoller.processQueue();

    verify(mockPoller, times(10)).submit(any(Runnable.class));
  }

  @Test
  @DisplayName("stop shuts down the poller gracefully when termination completes in time")
  void stopShutsDownPollerGracefully() throws InterruptedException {
    var mockPoller = getMockPoller();
    when(mockPoller.awaitTermination(1, TimeUnit.SECONDS)).thenReturn(true);

    mandateBatchProcessingPoller.stop();

    verify(mockPoller).shutdown();
    verify(mockPoller, never()).shutdownNow();
  }

  @Test
  @DisplayName("stop forces shutdown when termination does not complete in time")
  void stopForcesShutdownWhenTerminationTimesOut() throws InterruptedException {
    var mockPoller = getMockPoller();
    when(mockPoller.awaitTermination(1, TimeUnit.SECONDS)).thenReturn(false);

    mandateBatchProcessingPoller.stop();

    verify(mockPoller).shutdown();
    verify(mockPoller).shutdownNow();
  }

  @Test
  @DisplayName("Poller should do nothing when no context available")
  void pollerDoNothing() {
    var mockedQueue = getMockQueue();

    when(mockedQueue.poll()).thenReturn(null);

    var poller = mandateBatchProcessingPoller.getPoller();

    poller.run();

    verifyNoInteractions(mandateBatchCompletion);
    verify(mockedQueue, times(0)).add(any());
  }

  @Test
  @DisplayName("Poller should stop when max poll count exceeded")
  void pollerTimeout() {
    var locale = Locale.ENGLISH;

    Mandate mandate1 = sampleFundPensionOpeningMandate();
    Mandate mandate2 = samplePartialWithdrawalMandate();
    var mandateBatch = MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));

    var pollingContext = new MandateBatchPollingContext(locale, mandateBatch, MAX_POLL_COUNT + 1);

    var mockedQueue = getMockQueue();

    when(mockedQueue.poll()).thenReturn(pollingContext);

    var poller = mandateBatchProcessingPoller.getPoller();

    poller.run();

    verifyNoInteractions(mandateBatchCompletion);
    verify(mockedQueue, times(0)).add(any());
  }

  @Test
  @DisplayName("Poller should submit another context to queue when processing not finished")
  void pollerMandatesNotFinished() {
    var locale = Locale.ENGLISH;
    int contextCount = 3;

    Mandate mandate1 = sampleFundPensionOpeningMandate();
    Mandate mandate2 = samplePartialWithdrawalMandate();
    var mandateBatch = MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));

    var pollingContext = new MandateBatchPollingContext(locale, mandateBatch, contextCount);

    var mockedQueue = getMockQueue();

    when(mockedQueue.poll()).thenReturn(pollingContext);

    when(mandateProcessor.isFinished(mandate1)).thenReturn(false);

    var poller = mandateBatchProcessingPoller.getPoller();

    poller.run();

    verifyNoInteractions(mandateBatchCompletion);
    verify(mockedQueue, times(1))
        .add(
            argThat(
                context ->
                    context.batch().equals(mandateBatch) && context.count() == contextCount + 1));
  }

  @Test
  void pollerFinishesABatchOnlyInsideItsOwnersSecurityContext() {
    Mandate mandate1 = sampleFundPensionOpeningMandate();
    Mandate mandate2 = samplePartialWithdrawalMandate();
    var mandateBatch = MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));
    var mockedQueue = getMockQueue();
    when(mockedQueue.poll())
        .thenReturn(new MandateBatchPollingContext(Locale.ENGLISH, mandateBatch, 1));
    when(mandateProcessor.isFinished(any())).thenReturn(true);

    mandateBatchProcessingPoller.getPoller().run();

    verify(securityContextRunner).runAs(eq(mandate1.getUser()), any(Runnable.class));
    verifyNoInteractions(mandateBatchCompletion);
  }

  @Test
  void pollerCompletesAFinishedBatchAsItsOwner() {
    runActionsAsTheGivenPerson();
    var mandateBatch =
        MandateBatchFixture.aSavedMandateBatch(
            List.of(sampleFundPensionOpeningMandate(), samplePartialWithdrawalMandate()));
    var mockedQueue = getMockQueue();
    when(mockedQueue.poll())
        .thenReturn(new MandateBatchPollingContext(Locale.ENGLISH, mandateBatch, 1));
    when(mandateProcessor.isFinished(any())).thenReturn(true);

    mandateBatchProcessingPoller.getPoller().run();

    verify(mandateBatchCompletion).complete(mandateBatch, Locale.ENGLISH);
  }

  @Test
  void pollerReportsACompletionThatFailsAsAnErrorInsteadOfLosingItInTheExecutor() {
    var mandateBatch = aFinishedBatchOnTheQueue();
    willThrow(new IllegalStateException("Mandrill unavailable"))
        .given(mandateBatchCompletion)
        .complete(mandateBatch, Locale.ENGLISH);

    assertDoesNotThrow(() -> mandateBatchProcessingPoller.getPoller().run());

    assertThat(logEvents.list)
        .filteredOn(event -> event.getLevel() == Level.ERROR)
        .extracting(event -> event.getThrowableProxy().getClassName())
        .containsExactly(IllegalStateException.class.getName());
  }

  @Test
  void pollerDoesNotRaiseAnErrorForABatchPensionikeskusRejected() {
    var mandateBatch = aFinishedBatchOnTheQueue();
    willThrow(new MandateProcessingException(ErrorsResponse.ofSingleError("code", "rejected")))
        .given(mandateBatchCompletion)
        .complete(mandateBatch, Locale.ENGLISH);

    assertDoesNotThrow(() -> mandateBatchProcessingPoller.getPoller().run());

    assertThat(logEvents.list).filteredOn(event -> event.getLevel() == Level.ERROR).isEmpty();
  }

  private MandateBatch aFinishedBatchOnTheQueue() {
    runActionsAsTheGivenPerson();
    var mandateBatch =
        MandateBatchFixture.aSavedMandateBatch(List.of(samplePartialWithdrawalMandate()));
    when(getMockQueue().poll())
        .thenReturn(new MandateBatchPollingContext(Locale.ENGLISH, mandateBatch, 1));
    when(mandateProcessor.isFinished(any())).thenReturn(true);
    return mandateBatch;
  }

  @Test
  @DisplayName("Poller should not treat exactly max poll count as a timeout")
  void pollerAtMaxPollCountBoundaryIsNotTimedOut() {
    var locale = Locale.ENGLISH;

    Mandate mandate1 = sampleFundPensionOpeningMandate();
    Mandate mandate2 = samplePartialWithdrawalMandate();
    var mandateBatch = MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));

    var pollingContext = new MandateBatchPollingContext(locale, mandateBatch, MAX_POLL_COUNT);

    var mockedQueue = getMockQueue();

    when(mockedQueue.poll()).thenReturn(pollingContext);
    when(mandateProcessor.isFinished(mandate1)).thenReturn(false);

    var poller = mandateBatchProcessingPoller.getPoller();

    poller.run();

    verifyNoInteractions(mandateBatchCompletion);
    verify(mockedQueue, times(1))
        .add(
            argThat(
                context ->
                    context.batch().equals(mandateBatch) && context.count() == MAX_POLL_COUNT + 1));
  }
}
