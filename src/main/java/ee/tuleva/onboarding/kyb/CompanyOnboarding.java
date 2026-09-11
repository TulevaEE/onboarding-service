package ee.tuleva.onboarding.kyb;

import java.util.Optional;

public interface CompanyOnboarding {

  Optional<State> findState(String registryCode);

  enum State {
    PENDING,
    REJECTED,
    COMPLETED,
  }
}
