package ee.tuleva.onboarding.payment.event;

import ee.tuleva.onboarding.payment.Payment;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.user.User;
import java.util.Locale;
import lombok.Getter;

@Getter
public class PaymentCreatedEvent extends PaymentEvent {
  private final Payment payment;

  public PaymentCreatedEvent(Object source, User user, Payment payment, Locale locale) {
    super(source, user, locale);
    this.payment = payment;
  }

  @Override
  public PaymentData.PaymentType getPaymentType() {
    return this.payment.getPaymentType();
  }
}
