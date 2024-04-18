package ee.tuleva.onboarding.mandate.payment.rate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRateResponse {
  Long mandateId;
}
