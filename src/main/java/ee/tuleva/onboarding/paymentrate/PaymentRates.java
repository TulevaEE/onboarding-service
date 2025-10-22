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

  public boolean hasIncreased() {
    return (current != null && current > 2) || (pending != null && pending > 2);
  }

  public boolean canIncrease() {
    return !isMax();
  }

  private boolean isMax() {
    return (pending != null && pending == 6)
        || (current != null && current == 6 && pending == null);
  }
}
