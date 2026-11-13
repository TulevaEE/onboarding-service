package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.DOCUMENT;
import static ee.tuleva.onboarding.aml.AmlCheckType.OCCUPATION;
import static ee.tuleva.onboarding.aml.AmlCheckType.POLITICALLY_EXPOSED_PERSON;
import static ee.tuleva.onboarding.aml.AmlCheckType.POLITICALLY_EXPOSED_PERSON_AUTO;
import static ee.tuleva.onboarding.aml.AmlCheckType.POLITICALLY_EXPOSED_PERSON_OVERRIDE;
import static ee.tuleva.onboarding.aml.AmlCheckType.RESIDENCY_AUTO;
import static ee.tuleva.onboarding.aml.AmlCheckType.RESIDENCY_MANUAL;
import static ee.tuleva.onboarding.aml.AmlCheckType.SANCTION;
import static ee.tuleva.onboarding.aml.AmlCheckType.SANCTION_OVERRIDE;
import static ee.tuleva.onboarding.aml.AmlCheckType.SK_NAME;
import static java.util.stream.Collectors.toSet;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class MandateAmlGate {

  public record RecentChecks(Set<AmlCheckType> recorded, Set<AmlCheckType> passed) {

    public RecentChecks {
      recorded = Set.copyOf(recorded);
      passed = Set.copyOf(passed);
    }

    static RecentChecks of(List<AmlCheck> checks) {
      return new RecentChecks(
          checks.stream().map(AmlCheck::getType).collect(toSet()),
          checks.stream().filter(AmlCheck::isSuccess).map(AmlCheck::getType).collect(toSet()));
    }

    boolean passedAny(AmlCheckType... types) {
      return Stream.of(types).anyMatch(passed::contains);
    }
  }

  private MandateAmlGate() {}

  public static boolean passes(RecentChecks checks) {
    return (checks.recorded().contains(POLITICALLY_EXPOSED_PERSON)
            || checks.passedAny(
                POLITICALLY_EXPOSED_PERSON_AUTO, POLITICALLY_EXPOSED_PERSON_OVERRIDE))
        && checks.passedAny(SANCTION, SANCTION_OVERRIDE)
        && checks.passedAny(SK_NAME)
        && checks.passedAny(DOCUMENT)
        && checks.passedAny(OCCUPATION)
        && checks.passedAny(RESIDENCY_AUTO, RESIDENCY_MANUAL);
  }
}
