package ee.tuleva.onboarding.ariregister;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record Shareholding(BigDecimal ownershipPercent, List<@Nullable String> ownerCodes) {

  public boolean isHeldEntirelyBy(Collection<String> personalCodes) {
    return !ownerCodes.isEmpty()
        && ownerCodes.stream().allMatch(code -> code != null && personalCodes.contains(code));
  }
}
