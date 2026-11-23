package ee.tuleva.onboarding.mandate.email;

import ee.tuleva.onboarding.mandate.event.MandateBatchSignatureStored;
import ee.tuleva.onboarding.notification.email.PreviousContactNotices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class PreviousContactNoticeSender {

  private final PreviousContactNotices previousContactNotices;

  @EventListener
  public void sendWithdrawalNotice(MandateBatchSignatureStored event) {
    try {
      if (MandateEmailType.containsWithdrawal(event.mandateBatch())) {
        previousContactNotices.sendActivityNotice(event.user(), event.locale(), event.signedAt());
      }
    } catch (RuntimeException e) {
      log.error("Failed to notify previous contact: userId={}", event.user().getId(), e);
    }
  }
}
