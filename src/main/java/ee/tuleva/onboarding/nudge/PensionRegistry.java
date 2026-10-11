package ee.tuleva.onboarding.nudge;

import java.util.Optional;

@FunctionalInterface
public interface PensionRegistry {

  Optional<PensionRegistrySnapshot> snapshotFor(String personalCode);
}
