package ee.tuleva.onboarding.mandate;

import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ContactDetailsUpdater {

  private final MandateContacts mandateContacts;

  @EventListener
  public void updateAddress(AfterMandateSignedEvent event) {
    var user = event.getUser();
    mandateContacts.updateContactDetails(
        user, user.getEmail(), user.getPhoneNumber(), event.getAddress());
  }
}
