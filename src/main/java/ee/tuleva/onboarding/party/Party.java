package ee.tuleva.onboarding.party;

import ee.tuleva.onboarding.company.Company;
import ee.tuleva.onboarding.user.User;

/**
 * Implemented by {@link User} and {@link Company}. Use exhaustive switch to handle both types; add
 * a default branch that throws to catch new implementors at runtime.
 */
public interface Party {
  String code();

  String name();
}
