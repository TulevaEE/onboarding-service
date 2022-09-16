package ee.tuleva.onboarding.payment;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentReference {
  private String personalCode;
  private UUID uuid;
}
