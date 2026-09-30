package ee.tuleva.onboarding.banking.payment;

import java.math.BigDecimal;
import java.util.UUID;

public interface HeldPayouts {
  BigDecimal heldIn(UUID batchId);
}
