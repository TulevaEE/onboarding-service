package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.kyc.KycCheckPerformedEventOrder.PERSIST_AML_CHECK;

import ee.tuleva.onboarding.kyc.KycCheckPerformedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AmlKycCheckEventListener {

  private final AmlService amlService;

  @Order(PERSIST_AML_CHECK)
  @EventListener
  @Transactional
  public void onKycCheckPerformed(KycCheckPerformedEvent event) {
    amlService.addKycCheck(event.getPersonalCode(), event.getKycCheck());
  }
}
