package ee.tuleva.onboarding.mandate.event;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.address.Address;

public record BeforeMandateCreatedEvent(User user, Mandate mandate) {

  public Integer getPillar() {
    return mandate.getPillar();
  }

  public Address getAddress() {
    return mandate.getAddress();
  }

  public boolean isThirdPillar() {
    return mandate.isThirdPillar();
  }
}
