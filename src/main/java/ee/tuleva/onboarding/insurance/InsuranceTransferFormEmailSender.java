package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.insurance.InsuranceTransferEmailStatus.FAILED;
import static ee.tuleva.onboarding.insurance.InsuranceTransferEmailStatus.NO_EMAIL;
import static ee.tuleva.onboarding.insurance.InsuranceTransferEmailStatus.SENT;
import static ee.tuleva.onboarding.mandate.EmailVariablesAttachments.getNameMergeVars;
import static ee.tuleva.onboarding.notification.email.EmailType.INSURANCE_TRANSFER_FORM;
import static java.util.Objects.requireNonNull;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.notification.email.SignedContainerAttachment;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
class InsuranceTransferFormEmailSender {

  private final InsuranceTransferFormRepository repository;
  private final InsuranceTransferFormService formService;
  private final UserService userService;
  private final EmailService emailService;

  @TransactionalEventListener(phase = AFTER_COMMIT)
  public void onSigned(InsuranceTransferFormSigned event) {
    recordOutcome(event.formId(), sendOrFail(event));
  }

  private InsuranceTransferEmailStatus sendOrFail(InsuranceTransferFormSigned event) {
    try {
      return send(event);
    } catch (RuntimeException e) {
      log.error("Could not email the signed insurance transfer form: formId={}", event.formId(), e);
      return FAILED;
    }
  }

  private void recordOutcome(Long formId, InsuranceTransferEmailStatus status) {
    try {
      formService.recordEmailStatus(formId, status);
    } catch (RuntimeException e) {
      log.error(
          "Could not record how the insurance transfer form email ended: formId={}, status={}",
          formId,
          status,
          e);
    }
  }

  private InsuranceTransferEmailStatus send(InsuranceTransferFormSigned event) {
    InsuranceTransferForm form =
        repository
            .findById(event.formId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Signed insurance transfer form not found: formId=" + event.formId()));
    User user = userService.getByIdOrThrow(form.getUserId());
    String email = user.getEmail();
    if (email == null) {
      log.warn(
          "Person has no email for the signed insurance transfer form: formId={}", event.formId());
      return NO_EMAIL;
    }
    String templateName = INSURANCE_TRANSFER_FORM.getTemplateName(event.locale());
    MandrillMessage message =
        emailService.newMandrillMessage(
            email,
            templateName,
            mergeVars(user, form.getInsurer()),
            List.of("insurance_transfer"),
            List.of(
                SignedContainerAttachment.of(
                    form.getInsurer().signedFileBaseName(),
                    requireNonNull(
                        form.getSignedContainer(),
                        "Signed insurance transfer form missing: formId=" + event.formId()))));
    return emailService.send(user, message, templateName).isPresent() ? SENT : FAILED;
  }

  private static Map<String, Object> mergeVars(User user, Insurer insurer) {
    var mergeVars = new HashMap<>(getNameMergeVars(user));
    mergeVars.putAll(insurer.emailMergeVars());
    return mergeVars;
  }
}
