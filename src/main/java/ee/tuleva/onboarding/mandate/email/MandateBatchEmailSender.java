package ee.tuleva.onboarding.mandate.email;

import ee.tuleva.onboarding.mandate.event.AfterMandateBatchSignedEvent;
import ee.tuleva.onboarding.mandate.event.OnMandateBatchFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MandateBatchEmailSender {

  private final MandateBatchEmailService mandateBatchEmailService;

  @EventListener
  public void sendBatchEmail(AfterMandateBatchSignedEvent event) {
    mandateBatchEmailService.sendMandateBatch(
        event.getUser(), event.getMandateBatch(), event.getLocale());
  }

  @EventListener
  public void sendBatchFailedEmail(OnMandateBatchFailedEvent event) {
    mandateBatchEmailService.sendMandateBatchFailedEmail(
        event.getUser(), event.getMandateBatch(), event.getLocale());
  }
}
