package ee.tuleva.onboarding.mandate.batch.poller;

import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFundPensionOpeningMandate;
import static ee.tuleva.onboarding.mandate.MandateFixture.samplePartialWithdrawalMandate;
import static ee.tuleva.onboarding.mandate.batch.poller.MandateBatchProcessingPoller.MAX_POLL_COUNT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.batch.MandateBatchFixture;
import ee.tuleva.onboarding.mandate.batch.poller.MandateBatchProcessingPoller.MandateBatchPollingContext;
import ee.tuleva.onboarding.mandate.event.AfterMandateBatchSignedEvent;
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import ee.tuleva.onboarding.mandate.event.OnMandateBatchFailedEvent;
import ee.tuleva.onboarding.mandate.exception.MandateProcessingException;
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class MandateBatchProcessingPollerTest {

  @Mock private ApplicationEventPublisher applicationEventPublisher;
  @Mock private MandateProcessorService mandateProcessor;
  @Mock private EpisService episService;

  @InjectMocks private MandateBatchProcessingPoller mandateBatchProcessingPoller;

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

    verify(mockPoller, atLeast(1)).submit(any(Runnable.class));
  }

  @Test
  @DisplayName("Poller should do nothing when no context available")
  void pollerDoNothing() {
    var mockedQueue = getMockQueue();

    when(mockedQueue.poll()).thenReturn(null);

    var poller = mandateBatchProcessingPoller.getPoller();

    poller.run();

    verify(applicationEventPublisher, times(0)).publishEvent(any());
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

    verify(applicationEventPublisher, times(0)).publishEvent(any());
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

    verify(applicationEventPublisher, times(0)).publishEvent(any());
    verify(mockedQueue, times(1))
        .add(
            argThat(
                context ->
                    context.batch().equals(mandateBatch) && context.count() == contextCount + 1));
  }

  @Test
  @DisplayName("Poller should publish events on finished successful mandates")
  void pollerMandatesFinished() {
    var locale = Locale.ENGLISH;

    Mandate mandate1 = sampleFundPensionOpeningMandate();
    Mandate mandate2 = samplePartialWithdrawalMandate();
    var mandateBatch = MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));

    var pollingContext = new MandateBatchPollingContext(locale, mandateBatch, 1);

    var mockedQueue = getMockQueue();

    when(mockedQueue.poll()).thenReturn(pollingContext);

    when(mandateProcessor.isFinished(any())).thenReturn(true);
    when(mandateProcessor.getErrors(any())).thenReturn(new ErrorsResponse(List.of()));

    var poller = mandateBatchProcessingPoller.getPoller();

    poller.run();

    verify(episService, times(1)).clearCache(any());
    verify(applicationEventPublisher, times(0)).publishEvent(any(OnMandateBatchFailedEvent.class));
    verify(applicationEventPublisher, times(1))
        .publishEvent(
            argThat(
                (Object event) ->
                    event instanceof AfterMandateBatchSignedEvent
                        && ((AfterMandateBatchSignedEvent) event)
                            .mandateBatch()
                            .equals(mandateBatch)));
    verify(applicationEventPublisher, times(2)).publishEvent(any(AfterMandateSignedEvent.class));
  }

  @Test
  @DisplayName("Poller should not public events on finished but only mandates with errors")
  void pollerMandatesFinishedOnlyErrors() {
    var locale = Locale.ENGLISH;

    Mandate mandate1 = sampleFundPensionOpeningMandate();
    Mandate mandate2 = samplePartialWithdrawalMandate();
    var mandateBatch = MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));

    var pollingContext = new MandateBatchPollingContext(locale, mandateBatch, 1);

    var mockedQueue = getMockQueue();

    when(mockedQueue.poll()).thenReturn(pollingContext);

    when(mandateProcessor.isFinished(any())).thenReturn(true);
    when(mandateProcessor.getErrors(any()))
        .thenReturn(ErrorsResponse.ofSingleError("123", "Error"));

    var poller = mandateBatchProcessingPoller.getPoller();

    assertThrows(MandateProcessingException.class, poller::run);

    verify(episService, times(1)).clearCache(any());
    verify(applicationEventPublisher, times(0)).publishEvent(any(OnMandateBatchFailedEvent.class));
    verify(applicationEventPublisher, times(0))
        .publishEvent(any(AfterMandateBatchSignedEvent.class));
    verify(applicationEventPublisher, times(0)).publishEvent(any(AfterMandateSignedEvent.class));
  }

  @Test
  @DisplayName("Poller should publish event on finished and both successful and errored mandates")
  void pollerMandatesFinishedSomeErrors() {
    var locale = Locale.ENGLISH;

    Mandate mandate1 = sampleFundPensionOpeningMandate();
    Mandate mandate2 = samplePartialWithdrawalMandate();
    var mandateBatch = MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));

    var pollingContext = new MandateBatchPollingContext(locale, mandateBatch, 1);

    var mockedQueue = getMockQueue();

    when(mockedQueue.poll()).thenReturn(pollingContext);

    when(mandateProcessor.isFinished(any())).thenReturn(true);
    when(mandateProcessor.getErrors(eq(mandate1)))
        .thenReturn(ErrorsResponse.ofSingleError("123", "Error"));
    when(mandateProcessor.getErrors(eq(mandate2))).thenReturn(new ErrorsResponse(List.of()));

    var poller = mandateBatchProcessingPoller.getPoller();

    assertThrows(MandateProcessingException.class, poller::run);

    verify(episService, times(1)).clearCache(any());
    verify(applicationEventPublisher, times(1))
        .publishEvent(
            argThat(
                (Object event) ->
                    event instanceof OnMandateBatchFailedEvent
                        && ((OnMandateBatchFailedEvent) event)
                            .mandateBatch()
                            .equals(mandateBatch)));
    verify(applicationEventPublisher, times(0))
        .publishEvent(any(AfterMandateBatchSignedEvent.class));
    verify(applicationEventPublisher, times(0)).publishEvent(any(AfterMandateSignedEvent.class));
  }
}
