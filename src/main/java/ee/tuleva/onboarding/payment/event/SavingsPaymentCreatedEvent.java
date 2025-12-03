package ee.tuleva.onboarding.payment.event;

import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.user.User;
import java.util.Locale;

public class SavingsPaymentCreatedEvent extends PaymentEvent {
  public SavingsPaymentCreatedEvent(User user, Locale locale) {
    super(user, locale);
  }

  @Override
  public PaymentData.PaymentType getPaymentType() {
    return PaymentData.PaymentType.SAVINGS;
  }
}
