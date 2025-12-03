package ee.tuleva.onboarding.epis.contact;

import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ContactDetailsUpdater {

  private final ContactDetailsService contactDetailsService;

  @EventListener
  public void updateAddress(AfterMandateSignedEvent event) {
    contactDetailsService.updateContactDetails(event.user(), event.getAddress());
  }
}
