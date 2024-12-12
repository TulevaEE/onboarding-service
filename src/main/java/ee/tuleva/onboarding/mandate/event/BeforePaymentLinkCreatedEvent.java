package ee.tuleva.onboarding.mandate.event;

import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.address.Address;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class BeforePaymentLinkCreatedEvent extends ApplicationEvent {

  private final User user;
  private final Address address;

  public BeforePaymentLinkCreatedEvent(Object source, User user, Address address) {
    super(source);
    this.user = user;
    this.address = address;
  }
}
