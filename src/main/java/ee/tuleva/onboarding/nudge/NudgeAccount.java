package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.auth.role.RoleType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.auth.role.RoleType;

public record NudgeAccount(RoleType type, String code) {

  public static NudgeAccount self(Person person) {
    return new NudgeAccount(PERSON, person.getPersonalCode());
  }

  public static NudgeAccount person(String personalCode) {
    return new NudgeAccount(PERSON, personalCode);
  }

  public static NudgeAccount company(String registryCode) {
    return new NudgeAccount(LEGAL_ENTITY, registryCode);
  }

  public static NudgeAccount of(Role role) {
    return new NudgeAccount(role.type(), role.code());
  }

  public boolean isLegalEntity() {
    return type == LEGAL_ENTITY;
  }
}
