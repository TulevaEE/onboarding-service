package ee.tuleva.onboarding.payment;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class PaymentReference {
  private String personalCode;
  private UUID uuid;
}
