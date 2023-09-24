package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.MEMBER_FEE;

import ee.tuleva.onboarding.payment.event.PaymentCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEmailSender {

  private final PaymentEmailService emailService;

  @EventListener
  public void sendEmails(PaymentCreatedEvent event) {
    if (event.getPayment().getPaymentType() != MEMBER_FEE) {
      emailService.sendThirdPillarPaymentSuccessEmail(
          event.getUser(), event.getPayment(), event.getLocale());
    }
  }
}
