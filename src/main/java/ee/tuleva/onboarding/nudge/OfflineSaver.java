package ee.tuleva.onboarding.nudge;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.personalcode.PersonalCode;
import ee.tuleva.onboarding.user.User;

record OfflineSaver(String personalCode, boolean member) {

  static OfflineSaver of(User user) {
    return new OfflineSaver(user.getPersonalCode(), user.isMember());
  }

  static OfflineSaver registryOnly(Person person) {
    return new OfflineSaver(person.getPersonalCode(), false);
  }

  boolean adult() {
    return PersonalCode.getAge(personalCode) >= 18;
  }

  boolean reachedRetirementAge() {
    return PersonalCode.getAge(personalCode) >= PersonalCode.getRetirementAge(personalCode);
  }

  NudgeAccount account() {
    return NudgeAccount.person(personalCode);
  }
}
