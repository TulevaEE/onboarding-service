package ee.tuleva.onboarding.party;

import static java.util.Objects.requireNonNull;

public record Party(Type type, String code) {

  public enum Type {
    PERSON,
    LEGAL_ENTITY
  }

  public Party {
    requireNonNull(type);
    requireNonNull(code);
  }
}
