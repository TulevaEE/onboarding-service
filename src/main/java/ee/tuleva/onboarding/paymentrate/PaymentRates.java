package ee.tuleva.onboarding.paymentrate;

import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class PaymentRates {
  Integer current;
  Integer pending;

  public Optional<Integer> getPending() {
    return Optional.ofNullable(pending);
  }
}
