package ee.tuleva.onboarding.mandate.event;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.address.Address;
import java.util.Locale;

public record AfterMandateSignedEvent(User user, Mandate mandate, Locale locale) {

  public Integer getPillar() {
    return mandate.getPillar();
  }

  public Address getAddress() {
    return mandate.getAddress();
  }
}
