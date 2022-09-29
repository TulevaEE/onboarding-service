package ee.tuleva.onboarding.payment.provider;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
class PaymentReference {
  private String personalCode;
  private UUID uuid;
}
