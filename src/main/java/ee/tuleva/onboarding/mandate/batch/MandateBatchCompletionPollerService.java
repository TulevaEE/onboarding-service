package ee.tuleva.onboarding.mandate.batch;

import static java.util.concurrent.TimeUnit.SECONDS;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.error.response.ErrorResponse;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.event.AfterMandateBatchSignedEvent;
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import ee.tuleva.onboarding.mandate.event.OnMandateBatchFailedEvent;
import ee.tuleva.onboarding.mandate.exception.MandateProcessingException;
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService;
import ee.tuleva.onboarding.user.User;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MandateBatchCompletionPollerService {
  private final ApplicationEventPublisher applicationEventPublisher;
  private final MandateProcessorService mandateProcessor;
  private final EpisService episService;

  private final ExecutorService poller = Executors.newFixedThreadPool(20);

  private final Queue<MandateBatchPollingContext> batchPollingQueue = new ConcurrentLinkedQueue<>();

  private static final int MAX_POLL_COUNT = 60;

  record MandateBatchPollingContext(Locale locale, MandateBatch batch, Integer count) {

    User user() {
      return batch.getMandates().getFirst().getUser();
    }

    static MandateBatchPollingContext getNextPollingContext(MandateBatchPollingContext context) {
      return new MandateBatchPollingContext(context.locale, context.batch, context.count + 1);
    }
  }

  public void startPollingForBatchProcessingFinished(MandateBatch mandateBatch, Locale locale) {
    log.info(
        "Starting to poll for mandate batch (mandateBatchId={}) processing completion",
        mandateBatch.getId());
    batchPollingQueue.add(new MandateBatchPollingContext(locale, mandateBatch, 0));
  }

  // TODO deploy/shutdown would cause emails to get dropped, some form of persistence?
  @SneakyThrows
  @PreDestroy
  private void stop() {
    poller.shutdown();
    try {
      if (!poller.awaitTermination(1, SECONDS)) {
        poller.shutdownNow();
      }
    } catch (InterruptedException e) {
      poller.shutdownNow();
    }
  }

  private boolean haveAllBatchMandatesFinishedProcessing(MandateBatch mandateBatch) {
    return mandateBatch.getMandates().stream().allMatch(mandateProcessor::isFinished);
  }

  private Runnable getPoller() {
    return () -> {
      var context = batchPollingQueue.poll();

      if (context == null) {
        return;
      }

      if (context.count > MAX_POLL_COUNT) {
        log.error("Mandate batch (mandateBatchId={}) processing timed out", context.batch.getId());
        return;
      }

      if (!haveAllBatchMandatesFinishedProcessing(context.batch)) {
        log.info(
            "Mandate batch (mandateBatchId={}) processing not finished, polling again",
            context.batch.getId());
        batchPollingQueue.add(MandateBatchPollingContext.getNextPollingContext(context));
        return;
      }

      onMandateProcessingFinished(context);
    };
  }

  @Scheduled(fixedRate = 1000)
  private void processQueue() {
    // TODO how many pollers to create?
    for (int i = 0; i < batchPollingQueue.size(); i++) {
      poller.submit(getPoller());
    }
  }

  private void onMandateProcessingFinished(MandateBatchPollingContext context) {
    episService.clearCache(context.user());
    handleMandateProcessingErrors(context);
    notifyAboutSignedMandate(context);
  }

  private void handleMandateProcessingErrors(MandateBatchPollingContext context) {
    var mandates = context.batch.getMandates();

    List<ErrorResponse> errorResponses =
        mandates.stream()
            .map(mandate -> mandateProcessor.getErrors(mandate).getErrors())
            .flatMap(List::stream)
            .toList();

    int failedMandateCount =
        mandates.stream()
            .filter(mandate -> !mandateProcessor.getErrors(mandate).getErrors().isEmpty())
            .toList()
            .size();

    int successfulMandateCount = mandates.size() - failedMandateCount;

    ErrorsResponse errorsResponse = new ErrorsResponse(errorResponses);

    if (errorsResponse.hasErrors()) {
      log.info(
          "Mandate batch (mandateBatchId={}) processing errors {}",
          context.batch.getId(),
          errorsResponse);

      // only notify on inconsistent state: more than 1 mandate in batch, some were successful and
      // some weren't
      if (mandates.size() > 1 && successfulMandateCount > 0 && failedMandateCount > 0) {
        applicationEventPublisher.publishEvent(
            new OnMandateBatchFailedEvent(this, context.user(), context.batch, context.locale));
      }

      throw new MandateProcessingException(errorsResponse);
    }
  }

  private void notifyAboutSignedMandate(MandateBatchPollingContext context) {
    context
        .batch
        .getMandates()
        .forEach(
            mandate ->
                applicationEventPublisher.publishEvent(
                    new AfterMandateSignedEvent(this, context.user(), mandate, context.locale)));

    applicationEventPublisher.publishEvent(
        new AfterMandateBatchSignedEvent(this, context.user(), context.batch, context.locale));
  }
}
