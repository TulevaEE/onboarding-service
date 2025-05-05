package ee.tuleva.onboarding.payment.event;

import ee.tuleva.onboarding.payment.Payment;
import ee.tuleva.onboarding.payment.PaymentData.PaymentType;
import ee.tuleva.onboarding.user.User;
import java.util.Locale;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AfterPaymentDoneEvent extends ApplicationEvent {

  private final User user;
  private final Payment payment;
  private final Locale locale;

  public AfterPaymentDoneEvent(Object source, User user, Payment payment, Locale locale) {
    super(source);
    this.user = user;
    this.payment = payment;
    this.locale = locale;
  }

  public PaymentType getPaymentType() {
    return payment.getPaymentType();
  }
}
