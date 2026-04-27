package ee.tuleva.onboarding.payment;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.payment.provider.montonio.MontonioPaymentLinkGenerator;
import ee.tuleva.onboarding.payment.recurring.CoopPankPaymentLinkGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SinglePaymentLinkGenerator implements PaymentLinkGenerator {

  private final CoopPankPaymentLinkGenerator coopPankPaymentLinkGenerator;
  private final MontonioPaymentLinkGenerator montonioPaymentLinkGenerator;

  @Override
  public PaymentLink getPaymentLink(PaymentData paymentData, Person person) {
    if (paymentData.getPaymentChannel() == null) {
      throw new ErrorsResponseException(
          ErrorsResponse.ofSingleError(
              "payment.channel.required", "Payment channel is required for single payments."));
    }
    return switch (paymentData.getPaymentChannel()) {
      case PARTNER, COOP_WEB -> coopPankPaymentLinkGenerator.getPaymentLink(paymentData, person);
      default -> montonioPaymentLinkGenerator.getPaymentLink(paymentData, person);
    };
  }
}
