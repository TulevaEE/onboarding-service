package ee.tuleva.onboarding.party;

import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.auth.role.Role;

public record Party(Type type, String code) {

  public enum Type {
    PERSON,
    LEGAL_ENTITY
  }

  public Party {
    requireNonNull(type);
    requireNonNull(code);
  }

  public static Party from(Role role) {
    return new Party(Type.valueOf(role.type().name()), role.code());
  }
}
