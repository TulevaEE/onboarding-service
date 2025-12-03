package ee.tuleva.onboarding.payment.event;

import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.user.User;
import java.util.Locale;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public abstract class PaymentEvent {
  private final User user;
  private final Locale locale;

  public abstract PaymentData.PaymentType getPaymentType();
}
