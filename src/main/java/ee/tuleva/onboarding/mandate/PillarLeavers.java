package ee.tuleva.onboarding.mandate;

@FunctionalInterface
public interface PillarLeavers {

  boolean hasLeft(String personalCode);
}
