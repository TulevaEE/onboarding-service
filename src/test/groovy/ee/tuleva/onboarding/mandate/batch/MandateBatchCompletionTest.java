package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFundPensionOpeningMandate;
import static ee.tuleva.onboarding.mandate.MandateFixture.samplePartialWithdrawalMandate;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchFixture.aSavedMandateBatch;
import static java.util.Locale.ENGLISH;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.MandateContacts;
import ee.tuleva.onboarding.mandate.event.AfterMandateBatchSignedEvent;
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import ee.tuleva.onboarding.mandate.event.OnMandateBatchFailedEvent;
import ee.tuleva.onboarding.mandate.exception.MandateProcessingException;
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class MandateBatchCompletionTest {

  @Mock private ApplicationEventPublisher applicationEventPublisher;
  @Mock private MandateProcessorService mandateProcessor;
  @Mock private MandateContacts mandateContacts;
  @Mock private MandateBatchRepository mandateBatchRepository;

  @InjectMocks private MandateBatchCompletion mandateBatchCompletion;

  private final ErrorsResponse noErrors = new ErrorsResponse(List.of());
  private final ErrorsResponse anError = ErrorsResponse.ofSingleError("123", "Error");

  @Test
  void completingASuccessfulBatchClearsTheOwnersContactsAndNotifiesAboutEveryMandateAndTheBatch() {
    var mandate1 = sampleFundPensionOpeningMandate();
    var mandate2 = samplePartialWithdrawalMandate();
    var mandateBatch = aSavedMandateBatch(List.of(mandate1, mandate2));
    givenNoOtherPathCompletedIt(mandateBatch);
    given(mandateProcessor.getErrors(any())).willReturn(noErrors);

    mandateBatchCompletion.completeOnce(mandateBatch, ENGLISH);

    then(mandateContacts).should().clearCache(mandate1.getUser());
    then(applicationEventPublisher)
        .should(times(2))
        .publishEvent(any(AfterMandateSignedEvent.class));
    then(applicationEventPublisher)
        .should()
        .publishEvent(
            argThat(
                event ->
                    event instanceof AfterMandateBatchSignedEvent batchSigned
                        && batchSigned.getMandateBatch().equals(mandateBatch)));
    then(applicationEventPublisher)
        .should(never())
        .publishEvent(any(OnMandateBatchFailedEvent.class));
  }

  @Test
  void completingAFailedSingleMandateBatchNotifiesNobody() {
    var mandateBatch = aSavedMandateBatch(List.of(sampleFundPensionOpeningMandate()));
    givenNoOtherPathCompletedIt(mandateBatch);
    given(mandateProcessor.getErrors(any())).willReturn(anError);

    assertThatThrownBy(() -> mandateBatchCompletion.completeOnce(mandateBatch, ENGLISH))
        .isInstanceOf(MandateProcessingException.class);

    then(applicationEventPublisher).shouldHaveNoInteractions();
  }

  @Test
  void completingABatchWhoseEveryMandateFailedClearsTheContactsButNotifiesNobody() {
    var mandateBatch =
        aSavedMandateBatch(
            List.of(sampleFundPensionOpeningMandate(), samplePartialWithdrawalMandate()));
    givenNoOtherPathCompletedIt(mandateBatch);
    given(mandateProcessor.getErrors(any())).willReturn(anError);

    assertThatThrownBy(() -> mandateBatchCompletion.completeOnce(mandateBatch, ENGLISH))
        .isInstanceOf(MandateProcessingException.class);

    then(mandateContacts).should().clearCache(any());
    then(applicationEventPublisher).shouldHaveNoInteractions();
  }

  @Test
  void completingAPartlyFailedBatchReportsTheFailureInsteadOfTheSignedMandates() {
    var mandate1 = sampleFundPensionOpeningMandate();
    var mandate2 = samplePartialWithdrawalMandate();
    var mandateBatch = aSavedMandateBatch(List.of(mandate1, mandate2));
    givenNoOtherPathCompletedIt(mandateBatch);
    given(mandateProcessor.getErrors(mandate1)).willReturn(anError);
    given(mandateProcessor.getErrors(mandate2)).willReturn(noErrors);

    assertThatThrownBy(() -> mandateBatchCompletion.completeOnce(mandateBatch, ENGLISH))
        .isInstanceOf(MandateProcessingException.class);

    then(mandateContacts).should().clearCache(any());
    then(applicationEventPublisher)
        .should()
        .publishEvent(
            argThat(
                event ->
                    event instanceof OnMandateBatchFailedEvent failed
                        && failed.getMandateBatch().equals(mandateBatch)));
    then(applicationEventPublisher)
        .should(never())
        .publishEvent(any(AfterMandateBatchSignedEvent.class));
    then(applicationEventPublisher)
        .should(never())
        .publishEvent(any(AfterMandateSignedEvent.class));
  }

  @Test
  void leavesABatchThatAnotherPathAlreadyCompletedAlone() {
    var mandateBatch =
        aSavedMandateBatch(
            List.of(sampleFundPensionOpeningMandate(), samplePartialWithdrawalMandate()));
    given(mandateBatchRepository.markSignedBatchCompleted(mandateBatch.getId())).willReturn(0);

    mandateBatchCompletion.completeOnce(mandateBatch, ENGLISH);

    then(mandateContacts).shouldHaveNoInteractions();
    then(mandateProcessor).shouldHaveNoInteractions();
    then(applicationEventPublisher).shouldHaveNoInteractions();
  }

  private void givenNoOtherPathCompletedIt(MandateBatch mandateBatch) {
    given(mandateBatchRepository.markSignedBatchCompleted(mandateBatch.getId())).willReturn(1);
  }
}
