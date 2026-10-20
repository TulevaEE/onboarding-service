package ee.tuleva.onboarding.ariregister;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public record Shareholding(BigDecimal ownershipPercent, List<String> ownerCodes) {

  public boolean isHeldEntirelyBy(Collection<String> personalCodes) {
    return !ownerCodes.isEmpty() && personalCodes.containsAll(ownerCodes);
  }
}
