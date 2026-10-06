package ee.tuleva.onboarding.banking.message;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public record BookedBalance(BigDecimal amount, Instant asOf, Set<String> creditedEndToEndIds) {

  public boolean credits(String endToEndId) {
    return creditedEndToEndIds.contains(endToEndId);
  }
}
