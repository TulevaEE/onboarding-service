package ee.tuleva.onboarding.mandate.batch;

import ee.tuleva.onboarding.error.response.ErrorResponse;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.MandateContacts;
import ee.tuleva.onboarding.mandate.event.AfterMandateBatchSignedEvent;
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import ee.tuleva.onboarding.mandate.event.OnMandateBatchFailedEvent;
import ee.tuleva.onboarding.mandate.exception.MandateProcessingException;
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService;
import ee.tuleva.onboarding.user.User;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MandateBatchCompletion {

  private final ApplicationEventPublisher applicationEventPublisher;
  private final MandateProcessorService mandateProcessor;
  private final MandateContacts mandateContacts;

  public void complete(MandateBatch mandateBatch, Locale locale) {
    log.info(
        "Mandate batch processing finished, notifying: mandateBatchId={}", mandateBatch.getId());
    User owner = owner(mandateBatch);
    mandateContacts.clearCache(owner);
    handleMandateProcessingErrors(mandateBatch, owner, locale);
    notifyAboutSignedMandates(mandateBatch, owner, locale);
  }

  private static User owner(MandateBatch mandateBatch) {
    return mandateBatch.getMandates().getFirst().getUser();
  }

  private void handleMandateProcessingErrors(MandateBatch mandateBatch, User owner, Locale locale) {
    var mandates = mandateBatch.getMandates();

    List<ErrorResponse> errorResponses =
        mandates.stream()
            .map(mandate -> mandateProcessor.getErrors(mandate).getErrors())
            .flatMap(List::stream)
            .toList();

    long failedMandateCount =
        mandates.stream()
            .filter(mandate -> !mandateProcessor.getErrors(mandate).getErrors().isEmpty())
            .count();

    long successfulMandateCount = mandates.size() - failedMandateCount;

    ErrorsResponse errorsResponse = new ErrorsResponse(errorResponses);

    if (errorsResponse.hasErrors()) {
      log.info(
          "Mandate batch processing errors: mandateBatchId={}, errors={}",
          mandateBatch.getId(),
          errorsResponse);

      if (isPartlyFailed(mandates.size(), successfulMandateCount, failedMandateCount)) {
        applicationEventPublisher.publishEvent(
            new OnMandateBatchFailedEvent(this, owner, mandateBatch, locale));
      }

      throw new MandateProcessingException(errorsResponse);
    }
  }

  private static boolean isPartlyFailed(
      int mandateCount, long successfulMandateCount, long failedMandateCount) {
    return mandateCount > 1 && successfulMandateCount > 0 && failedMandateCount > 0;
  }

  private void notifyAboutSignedMandates(MandateBatch mandateBatch, User owner, Locale locale) {
    mandateBatch
        .getMandates()
        .forEach(
            mandate ->
                applicationEventPublisher.publishEvent(
                    new AfterMandateSignedEvent(this, owner, mandate, locale)));

    applicationEventPublisher.publishEvent(
        new AfterMandateBatchSignedEvent(this, owner, mandateBatch, locale));
  }
}
