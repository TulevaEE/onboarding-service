package ee.tuleva.onboarding.paymentrate;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class PaymentRates {
  Integer current;
  Integer pending;
}
