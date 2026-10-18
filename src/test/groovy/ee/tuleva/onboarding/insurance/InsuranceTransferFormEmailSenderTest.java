package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.insurance.InsuranceTransferEmailStatus.FAILED;
import static ee.tuleva.onboarding.insurance.InsuranceTransferEmailStatus.NO_EMAIL;
import static ee.tuleva.onboarding.insurance.InsuranceTransferEmailStatus.SENT;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.signedForm;
import static ee.tuleva.onboarding.insurance.Insurer.ERGO;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InsuranceTransferFormEmailSenderTest {

  @Mock private InsuranceTransferFormRepository repository;
  @Mock private InsuranceTransferFormService formService;
  @Mock private UserService userService;
  @Mock private EmailService emailService;

  @InjectMocks private InsuranceTransferFormEmailSender sender;

  private final User user = sampleUser().build();
  private final MandrillMessage message = new MandrillMessage();

  @Test
  void emailsTheSignedContainerWithTheInsurerToForwardItToAndRecordsItAsSent() {
    givenASignedErgoForm();
    given(
            emailService.newMandrillMessage(
                eq(user.getEmail()),
                eq("insurance_transfer_form_en"),
                eq(
                    Map.of(
                        "fname", "Jordan",
                        "lname", "Valdma",
                        "insurerName", "Ergo",
                        "insurerEmail", "elukindlustus@ergo.ee")),
                eq(List.of("insurance_transfer")),
                argThat(
                    attachments ->
                        attachments.size() == 1
                            && attachments
                                .getFirst()
                                .getName()
                                .equals("Tuleva_Ergo_avaldus.asice"))))
        .willReturn(message);
    given(emailService.send(user, message, "insurance_transfer_form_en"))
        .willReturn(Optional.of(new MandrillMessageStatus()));

    sender.onSigned(new InsuranceTransferFormSigned(1L, Locale.ENGLISH));

    then(formService).should().recordEmailStatus(1L, SENT);
  }

  @Test
  void recordsAFailedEmailWhenMandrillDoesNotAcceptItSoThePersonCanDownloadTheFileInstead() {
    givenASignedErgoForm();
    given(emailService.newMandrillMessage(any(), any(), anyMap(), anyList(), anyList()))
        .willReturn(message);
    given(emailService.send(user, message, "insurance_transfer_form_et"))
        .willReturn(Optional.empty());

    sender.onSigned(new InsuranceTransferFormSigned(1L, Locale.of("et")));

    then(formService).should().recordEmailStatus(1L, FAILED);
  }

  @Test
  void recordsThatThePersonHasNoEmailWithoutSendingAnything() {
    given(repository.findById(1L)).willReturn(Optional.of(signedForm(1L, user.getId(), ERGO)));
    given(userService.getByIdOrThrow(user.getId())).willReturn(sampleUser().email(null).build());

    sender.onSigned(new InsuranceTransferFormSigned(1L, Locale.of("et")));

    then(formService).should().recordEmailStatus(1L, NO_EMAIL);
    then(emailService).shouldHaveNoInteractions();
  }

  @Test
  void keepsTheSigningSuccessfulWhenTheEmailCannotBeHandled() {
    givenASignedErgoForm();
    given(emailService.newMandrillMessage(any(), any(), anyMap(), anyList(), anyList()))
        .willReturn(message);
    given(emailService.send(user, message, "insurance_transfer_form_et"))
        .willReturn(Optional.of(new MandrillMessageStatus()));
    willThrow(new IllegalStateException("database down"))
        .given(formService)
        .recordEmailStatus(1L, SENT);

    assertThatCode(() -> sender.onSigned(new InsuranceTransferFormSigned(1L, Locale.of("et"))))
        .doesNotThrowAnyException();
  }

  private void givenASignedErgoForm() {
    given(repository.findById(1L)).willReturn(Optional.of(signedForm(1L, user.getId(), ERGO)));
    given(userService.getByIdOrThrow(user.getId())).willReturn(user);
  }
}
