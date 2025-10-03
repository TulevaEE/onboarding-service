package ee.tuleva.onboarding.payment.event;

import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.user.User;
import java.util.Locale;

public class SavingsPaymentFailedEvent extends PaymentEvent {
  public SavingsPaymentFailedEvent(Object source, User user, Locale locale) {
    super(source, user, locale);
  }

  @Override
  public PaymentData.PaymentType getPaymentType() {
    return PaymentData.PaymentType.SAVINGS;
  }
}
