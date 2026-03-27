package ee.tuleva.onboarding.party;

import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.auth.role.Role;

public record PartyId(Type type, String code) {

  public enum Type {
    PERSON,
    LEGAL_ENTITY
  }

  public PartyId {
    requireNonNull(type);
    requireNonNull(code);
  }

  public static PartyId from(Role role) {
    return new PartyId(Type.valueOf(role.type().name()), role.code());
  }
}
