package ee.tuleva.onboarding.user.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@AllArgsConstructor
@Data
public class PaymentRatesResponse {
  @Nullable Integer current;
  @Nullable Integer pending;
}
