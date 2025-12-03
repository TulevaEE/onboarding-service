package ee.tuleva.onboarding.aml;

import ee.tuleva.onboarding.kyc.KycCheckPerformedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AmlKycCheckEventListener {

  private final AmlService amlService;

  @EventListener
  @Transactional
  public void onKycCheckPerformed(KycCheckPerformedEvent event) {
    amlService.addKycCheck(event.personalCode(), event.kycCheck());
  }
}
