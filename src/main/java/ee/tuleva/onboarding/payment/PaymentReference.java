package ee.tuleva.onboarding.payment;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class PaymentReference {
  private String personalCode;
  private UUID uuid;
}
