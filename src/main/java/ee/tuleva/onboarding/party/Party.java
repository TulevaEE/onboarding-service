package ee.tuleva.onboarding.party;

/**
 * Implemented by User and Company. Use exhaustive switch to handle both types; add a default branch
 * that throws to catch new implementors at runtime.
 */
public interface Party {
  String code();

  String name();
}
