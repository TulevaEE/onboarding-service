package ee.tuleva.onboarding.mandate.batch.poller;

import static java.util.concurrent.TimeUnit.SECONDS;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.error.response.ErrorResponse;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
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
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MandateBatchProcessingPoller {
  private final ApplicationEventPublisher applicationEventPublisher;
  private final MandateProcessorService mandateProcessor;
  private final EpisService episService;

  private final ExecutorService poller = Executors.newFixedThreadPool(THREAD_COUNT);

  private final Queue<MandateBatchPollingContext> batchPollingQueue = new ConcurrentLinkedQueue<>();

  protected static final int MAX_POLL_COUNT = 60;
  private static final int THREAD_COUNT = 10;

  protected record MandateBatchPollingContext(Locale locale, MandateBatch batch, Integer count) {

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
  protected void stop() {
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

  protected Runnable getPoller() {
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
  @SchedulerLock(
      name = "MandateBatchProcessingPoller_processQueue",
      lockAtMostFor = "500ms",
      lockAtLeastFor = "100ms")
  public void processQueue() {
    for (int i = 0; i < THREAD_COUNT; i++) {
      poller.submit(getPoller());
    }
  }

  protected void onMandateProcessingFinished(MandateBatchPollingContext context) {
    log.info(
        "Mandate batch (mandateBatchId={}) processing finished, notifying", context.batch.getId());
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
            new OnMandateBatchFailedEvent(context.user(), context.batch, context.locale));
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
                    new AfterMandateSignedEvent(context.user(), mandate, context.locale)));

    applicationEventPublisher.publishEvent(
        new AfterMandateBatchSignedEvent(context.user(), context.batch, context.locale));
  }
}
