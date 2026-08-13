package ee.tuleva.onboarding.banking.payment;

import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class PaymentIntegrityException extends RuntimeException {

  public PaymentIntegrityException(String endToEndId, List<PaymentIntegrityViolation> violations) {
    super(
        "Payment file does not match the payment request: endToEndId=%s, violations=%s"
            .formatted(
                endToEndId, violations.stream().map(PaymentIntegrityViolation::summary).toList()));
  }
}
