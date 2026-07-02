package ee.tuleva.onboarding.ariregister;

import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record BeneficialOwners(List<BeneficialOwner> owners, int hiddenCount) {

  public static BeneficialOwners none() {
    return new BeneficialOwners(List.of(), 0);
  }
}
