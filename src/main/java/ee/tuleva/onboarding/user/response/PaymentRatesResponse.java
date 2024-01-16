package ee.tuleva.onboarding.user.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class PaymentRatesResponse {
  Integer current;
  Integer pending;
}
