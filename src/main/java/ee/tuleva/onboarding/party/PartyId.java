package ee.tuleva.onboarding.party;

import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
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
    Type type =
        switch (role.type()) {
          case PERSON -> Type.PERSON;
          case LEGAL_ENTITY -> Type.LEGAL_ENTITY;
        };
    return new PartyId(type, role.code());
  }

  public static PartyId from(AuthenticatedPerson person) {
    return from(
        requireNonNull(person.getRole(), "Role missing: personalCode=" + person.getPersonalCode()));
  }
}
