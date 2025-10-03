package ee.tuleva.onboarding.payment.event;

import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.user.User;
import java.util.Locale;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public abstract class PaymentEvent extends ApplicationEvent {
  private final User user;
  private final Locale locale;

  public PaymentEvent(Object source, User user, Locale locale) {
    super(source);
    this.user = user;
    this.locale = locale;
  }

  public abstract PaymentData.PaymentType getPaymentType();
}
