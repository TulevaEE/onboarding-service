package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.insurance.InsuranceTransferEmailStatus.FAILED;
import static ee.tuleva.onboarding.insurance.InsuranceTransferEmailStatus.SENT;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import ee.tuleva.onboarding.notification.email.AtMostOnceEmailSender;
import ee.tuleva.onboarding.notification.email.EmailSendResult;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
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
  private final AtMostOnceEmailSender insurerRequestEmailTransport;

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
    EmailSendResult result = insurerRequestEmailTransport.send(InsurerRequestEmail.of(form, user));
    if (result.outcome() == EmailSendResult.Outcome.SENT) {
      return SENT;
    }
    log.error(
        "Insurer was not sent the signed insurance transfer form: formId={}, insurer={}, outcome={}, problem={}",
        event.formId(),
        form.getInsurer(),
        result.outcome(),
        result.problem());
    return FAILED;
  }
}
