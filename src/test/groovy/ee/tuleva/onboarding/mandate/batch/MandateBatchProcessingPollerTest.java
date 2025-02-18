package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFundPensionOpeningMandate;
import static ee.tuleva.onboarding.mandate.MandateFixture.samplePartialWithdrawalMandate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.batch.MandateBatchProcessingPoller.MandateBatchPollingContext;
import ee.tuleva.onboarding.mandate.event.AfterMandateBatchSignedEvent;
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@Disabled
@ExtendWith(MockitoExtension.class)
class MandateBatchProcessingPollerTest {

  @Mock private ApplicationEventPublisher applicationEventPublisher;
  @Mock private MandateProcessorService mandateProcessor;
  @Mock private EpisService episService;

  @InjectMocks private MandateBatchProcessingPoller mandateBatchProcessingPoller;

  @SneakyThrows
  // @SuppressWarnings("unchecked")
  private ExecutorService getExecutor() {
    var field = MandateBatchProcessingPoller.class.getDeclaredField("poller");

    field.setAccessible(true);
    return (ExecutorService) field.get(mandateBatchProcessingPoller);
  }

  @SneakyThrows
  // @SuppressWarnings("unchecked")
  private Queue<MandateBatchPollingContext> getQueue() {
    var field = MandateBatchProcessingPoller.class.getDeclaredField("batchPollingQueue");

    field.setAccessible(true);
    return (Queue<MandateBatchPollingContext>) field.get(mandateBatchProcessingPoller);
  }

  @Test
  @DisplayName("Should start polling")
  @SneakyThrows
  void shouldPoll() {
    var locale = Locale.ENGLISH;

    Mandate mandate1 = sampleFundPensionOpeningMandate();
    Mandate mandate2 = samplePartialWithdrawalMandate();

    var mandateBatch = MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));

    when(mandateProcessor.isFinished(any())).thenReturn(true);
    //    when(mandateProcessor.isFinished(eq(mandate2))).thenReturn(false, true, true);

    /*when(mandateProcessor.getErrors(eq(mandate1))).thenReturn(new ErrorsResponse());
    when(mandateProcessor.getErrors(eq(mandate2))).thenReturn(new ErrorsResponse());*/

    /* var executor = getExecutor();
    var queue = getQueue();*/

    mandateBatchProcessingPoller.startPollingForBatchProcessingFinished(mandateBatch, locale);

    mandateBatchProcessingPoller.processQueue();

    long startTime = System.currentTimeMillis();
    long timeout = 5000;

    while (System.currentTimeMillis() - startTime < timeout) {
      mandateBatchProcessingPoller.processQueue();
      Thread.sleep(100);

      try {
        verify(applicationEventPublisher, atLeastOnce())
            .publishEvent(any(AfterMandateBatchSignedEvent.class));
        verify(applicationEventPublisher, atLeast(2))
            .publishEvent(any(AfterMandateSignedEvent.class));
        break;
      } catch (AssertionError ignored) {
      }
    }
  }
}
