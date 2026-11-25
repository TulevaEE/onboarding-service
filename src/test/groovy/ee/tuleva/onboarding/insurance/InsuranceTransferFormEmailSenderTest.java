package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.insurance.InsuranceTransferEmailStatus.FAILED;
import static ee.tuleva.onboarding.insurance.InsuranceTransferEmailStatus.SENT;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.signedForm;
import static ee.tuleva.onboarding.insurance.Insurer.ERGO;
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.notification.email.AtMostOnceEmailSender;
import ee.tuleva.onboarding.notification.email.EmailSendResult;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InsuranceTransferFormEmailSenderTest {

  @Mock private InsuranceTransferFormRepository repository;
  @Mock private InsuranceTransferFormService formService;
  @Mock private UserService userService;
  @Mock private AtMostOnceEmailSender insurerRequestEmailTransport;

  @InjectMocks private InsuranceTransferFormEmailSender sender;

  private final User user = sampleUser().build();

  @Test
  void emailsTheSignedContainerToTheInsurerAndRecordsItAsSent() {
    givenASignedErgoForm();
    given(
            insurerRequestEmailTransport.send(
                argThat(
                    message ->
                        message.getTo().getFirst().getEmail().equals("elukindlustus@ergo.ee")
                            && message
                                .getAttachments()
                                .getFirst()
                                .getName()
                                .equals("Tuleva_Ergo_avaldus.asice"))))
        .willReturn(result(Outcome.SENT));

    sender.onSigned(new InsuranceTransferFormSigned(1L, Locale.ENGLISH));

    then(formService).should().recordEmailStatus(1L, SENT);
  }

  @ParameterizedTest
  @EnumSource(
      value = Outcome.class,
      names = {"FAILED", "UNKNOWN"})
  void
      recordsAFailedEmailWhenTheInsurerIsNotConfirmedToHaveBeenSentItSoThePersonCanSendItThemselves(
          Outcome outcome) {
    givenASignedErgoForm();
    given(insurerRequestEmailTransport.send(any())).willReturn(result(outcome));

    sender.onSigned(new InsuranceTransferFormSigned(1L, Locale.of("et")));

    then(formService).should().recordEmailStatus(1L, FAILED);
  }

  @Test
  void stillEmailsTheInsurerWhenThePersonHasNoEmail() {
    given(repository.findById(1L)).willReturn(Optional.of(signedForm(1L, user.getId(), ERGO)));
    given(userService.getByIdOrThrow(user.getId())).willReturn(sampleUser().email(null).build());
    given(insurerRequestEmailTransport.send(any())).willReturn(result(Outcome.SENT));

    sender.onSigned(new InsuranceTransferFormSigned(1L, Locale.of("et")));

    then(formService).should().recordEmailStatus(1L, SENT);
  }

  @Test
  void recordsAFailedEmailWhenTheSignedFormCannotBeReadForSending() {
    given(repository.findById(1L)).willThrow(new IllegalStateException("database down"));

    sender.onSigned(new InsuranceTransferFormSigned(1L, Locale.of("et")));

    then(formService).should().recordEmailStatus(1L, FAILED);
    then(insurerRequestEmailTransport).shouldHaveNoInteractions();
  }

  @Test
  void keepsTheSigningSuccessfulAndASentEmailUnmarkedAsFailedWhenItsOutcomeCannotBeRecorded() {
    givenASignedErgoForm();
    given(insurerRequestEmailTransport.send(any())).willReturn(result(Outcome.SENT));
    willThrow(new IllegalStateException("database down"))
        .given(formService)
        .recordEmailStatus(1L, SENT);

    assertThatCode(() -> sender.onSigned(new InsuranceTransferFormSigned(1L, Locale.of("et"))))
        .doesNotThrowAnyException();
    then(formService).should(never()).recordEmailStatus(1L, FAILED);
  }

  private void givenASignedErgoForm() {
    given(repository.findById(1L)).willReturn(Optional.of(signedForm(1L, user.getId(), ERGO)));
    given(userService.getByIdOrThrow(user.getId())).willReturn(user);
  }

  private static EmailSendResult result(Outcome outcome) {
    return new EmailSendResult(outcome, List.of(), outcome == Outcome.SENT ? null : "refused");
  }
}
