package ee.tuleva.onboarding.mandate.event;

import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.address.Address;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class BeforePaymentLinkCreatedEvent extends ApplicationEvent {

  private final User user;
  private final Address address;
  private final PaymentData paymentData;

  public BeforePaymentLinkCreatedEvent(
      Object source, User user, Address address, PaymentData paymentData) {
    super(source);
    this.user = user;
    this.address = address;
    this.paymentData = paymentData;
  }
}
